// End-to-end Flexible Sync (FLX) isolation check for Barq.
//
// Drives the real sync server (launched by run_flx_e2e.sh, with FLX enabled and
// per-table rules) through the native SDK to prove the classic "shared catalog,
// private orders, back-office sees everything" model on ONE shared server file:
//
//   Rules configured on the server:
//     Catalog -> public-read-only   (every user may read; only admin may write)
//     Order   -> owner:owner_id     (a row is visible/writable only to the user
//                                     whose identity equals its owner_id field)
//
//   1. Admin seeds two Catalog products (allowed: admin may write a public table).
//   2. user_0 subscribes to Order + Catalog, creates its OWN order, and sees only
//      that order plus both products.
//   3. user_1 does the same and sees only ITS order plus both products.
//   4. Cross-check: user_0 must NOT see user_1's order and vice-versa, even though
//      all three share the exact same server file -> per-user isolation.
//   5. Back-office (an admin token) subscribes and sees BOTH orders -> aggregation.
//
// Each client authenticates with a signed JWT minted by run_flx_e2e.sh. The
// server derives the shared file path and the caller's identity from the token;
// the SDK never sends a partition (this is Flexible Sync, not partition sync).

#include <barq_native/sdk.hpp>

#include <chrono>
#include <fstream>
#include <future>
#include <iostream>
#include <sstream>
#include <string>

namespace barq::native {

// Class name maps to server table "class_Catalog"; the server rule names it
// "Catalog". Public read-only: everyone downloads it, only an admin may write.
struct Catalog {
    primary_key<int64_t> _id;
    std::string name;
};
BARQ_SCHEMA(Catalog, _id, name)

// Class name maps to "class_Order"; the server rule "Order:owner_id" makes a row
// visible and writable only to the user whose identity equals owner_id.
struct Order {
    primary_key<int64_t> _id;
    std::string owner_id;
    std::string item;
};
BARQ_SCHEMA(Order, _id, owner_id, item)

} // namespace barq::native

namespace {

using namespace barq::native;

int g_failures = 0;

void check(bool ok, const std::string& what)
{
    std::cout << (ok ? "  PASS  " : "  FAIL  ") << what << "\n";
    if (!ok)
        ++g_failures;
}

std::string read_token(const std::string& path)
{
    std::ifstream in(path);
    if (!in)
        throw std::runtime_error("cannot read token file: " + path);
    std::stringstream ss;
    ss << in.rdbuf();
    std::string tok = ss.str();
    while (!tok.empty() && (tok.back() == '\n' || tok.back() == '\r' || tok.back() == ' '))
        tok.pop_back();
    return tok;
}

// Opens a Flexible Sync database for one user against the shared server file.
// There is no partition: the server file and the caller's identity both come
// from the signed token.
db open_flx(const std::string& tenant_id, const std::string& user_id, const std::string& token,
            const std::string& route, const std::string& path)
{
    sync_user user(tenant_id, user_id, token);
    user.set_route(route);
    sync_config sc = user.make_flexible_sync_config();

    db_config cfg;
    cfg.set_path(path);
    cfg.set_sync_config(sc);
    return db(std::move(cfg));
}

// Waits on a future with a hard timeout so a broken sync can never hang the
// example. Returns false on timeout or error.
template <class T>
bool wait_ready(std::future<T> fut, int seconds, const std::string& label)
{
    if (fut.wait_for(std::chrono::seconds(seconds)) != std::future_status::ready) {
        std::cerr << "  ....  timeout after " << seconds << "s waiting for " << label << "\n";
        return false;
    }
    try {
        fut.get();
        return true;
    }
    catch (const std::exception& e) {
        std::cerr << "  ....  error waiting for " << label << ": " << e.what() << "\n";
        return false;
    }
}

const int kTimeout = 30;

// Subscribe to all Orders and all Catalog rows. The server intersects each
// subscription with its rule, so "all Orders" really means "all Orders I own".
bool subscribe_catalog_and_orders(db& d, const std::string& label)
{
    auto fut = d.subscriptions().update([](mutable_sync_subscription_set& mut) {
        mut.add<Catalog>("catalog");
        mut.add<Order>("orders");
    });
    return wait_ready(std::move(fut), kTimeout, label + " subscription bootstrap");
}

bool sync_down(db& d, const std::string& label)
{
    auto session = d.get_sync_session();
    if (!session) {
        std::cerr << "  ....  " << label << " has no sync session\n";
        return false;
    }
    bool ok = wait_ready(session->wait_for_download_completion(), kTimeout, label + " download");
    d.refresh();
    return ok;
}

bool sync_up(db& d, const std::string& label)
{
    auto session = d.get_sync_session();
    if (!session) {
        std::cerr << "  ....  " << label << " has no sync session\n";
        return false;
    }
    return wait_ready(session->wait_for_upload_completion(), kTimeout, label + " upload");
}

size_t count_orders_owned_by(db& d, const std::string& owner)
{
    size_t n = 0;
    for (auto o : d.objects<Order>()) {
        if (std::string(o.owner_id) == owner)
            ++n;
    }
    return n;
}

} // namespace

int main(int argc, char** argv)
{
    using namespace barq::native;

    if (argc < 4) {
        std::cerr << "usage: barq_flx_e2e <server_ws_url> <tokens_dir> <work_dir>\n";
        return 2;
    }
    const std::string route = argv[1];
    const std::string tokens_dir = argv[2];
    const std::string work_dir = argv[3];
    const std::string tenant = "shop"; // local file bookkeeping only; real tenant is in the token

    std::string tok_admin, tok_u0, tok_u1, tok_office;
    try {
        tok_admin = read_token(tokens_dir + "/admin.jwt");
        tok_u0 = read_token(tokens_dir + "/user_0.jwt");
        tok_u1 = read_token(tokens_dir + "/user_1.jwt");
        tok_office = read_token(tokens_dir + "/backoffice.jwt");
    }
    catch (const std::exception& e) {
        std::cerr << "fatal: " << e.what() << "\n";
        return 2;
    }

    std::cout << "Barq Flexible Sync e2e - route=" << route << "\n\n";

    try {
        // --- Step 1: admin seeds the shared catalog (a public read-only table).
        std::cout << "[1] admin seeds the shared catalog\n";
        db admin = open_flx(tenant, "admin", tok_admin, route, work_dir + "/admin.barq");
        check(subscribe_catalog_and_orders(admin, "admin"), "admin subscribed");
        admin.write([&] {
            Catalog a;
            a._id = 10;
            a.name = "coffee-beans";
            admin.add(std::move(a));
            Catalog b;
            b._id = 11;
            b.name = "tea-leaves";
            return admin.add(std::move(b));
        });
        check(sync_up(admin, "admin"), "admin uploaded 2 catalog products");

        // --- Step 2: user_0 creates its own order and views its data.
        std::cout << "\n[2] user_0 creates its own order\n";
        db u0 = open_flx(tenant, "user_0", tok_u0, route, work_dir + "/user_0.barq");
        check(subscribe_catalog_and_orders(u0, "user_0"), "user_0 subscribed");
        u0.write([&] {
            Order o;
            o._id = 1;
            o.owner_id = "user_0"; // must equal the token identity or the server undoes it
            o.item = "coffee-beans";
            return u0.add(std::move(o));
        });
        check(sync_up(u0, "user_0"), "user_0 uploaded its order");

        // --- Step 3: user_1 creates its own order and views its data.
        std::cout << "\n[3] user_1 creates its own order\n";
        db u1 = open_flx(tenant, "user_1", tok_u1, route, work_dir + "/user_1.barq");
        check(subscribe_catalog_and_orders(u1, "user_1"), "user_1 subscribed");
        u1.write([&] {
            Order o;
            o._id = 2;
            o.owner_id = "user_1";
            o.item = "tea-leaves";
            return u1.add(std::move(o));
        });
        check(sync_up(u1, "user_1"), "user_1 uploaded its order");

        // --- Step 4: everyone re-syncs; check per-user isolation on one file.
        std::cout << "\n[4] isolation: each user sees only its own order (plus the shared catalog)\n";
        check(sync_down(u0, "user_0"), "user_0 re-synced");
        check(sync_down(u1, "user_1"), "user_1 re-synced");

        check(u0.objects<Catalog>().size() == 2, "user_0 sees both shared products");
        check(u1.objects<Catalog>().size() == 2, "user_1 sees both shared products");

        check(u0.objects<Order>().size() == 1, "user_0 sees exactly 1 order");
        check(count_orders_owned_by(u0, "user_0") == 1, "user_0 sees its OWN order");
        check(count_orders_owned_by(u0, "user_1") == 0, "user_0 does NOT see user_1's order");

        check(u1.objects<Order>().size() == 1, "user_1 sees exactly 1 order");
        check(count_orders_owned_by(u1, "user_1") == 1, "user_1 sees its OWN order");
        check(count_orders_owned_by(u1, "user_0") == 0, "user_1 does NOT see user_0's order");

        // --- Step 5: back-office (admin) aggregates across all users.
        std::cout << "\n[5] back-office (admin token) sees every user's orders\n";
        db office = open_flx(tenant, "backoffice", tok_office, route, work_dir + "/backoffice.barq");
        check(subscribe_catalog_and_orders(office, "backoffice"), "back-office subscribed");
        check(sync_down(office, "backoffice"), "back-office re-synced");
        check(office.objects<Order>().size() == 2, "back-office sees BOTH orders (aggregation)");
        check(count_orders_owned_by(office, "user_0") == 1, "back-office sees user_0's order");
        check(count_orders_owned_by(office, "user_1") == 1, "back-office sees user_1's order");
        check(office.objects<Catalog>().size() == 2, "back-office sees the catalog");
    }
    catch (const std::exception& e) {
        std::cerr << "  ....  exception: " << e.what() << "\n";
        check(false, "ran without throwing");
    }

    std::cout << "\n" << (g_failures == 0 ? "ALL CHECKS PASSED" : "FAILURES: " + std::to_string(g_failures)) << "\n";
    return g_failures == 0 ? 0 : 1;
}
