// End-to-end multi-tenancy + sync check for Barq.
//
// Drives the real sync server (launched by run_e2e.sh) through the native SDK:
//   1. Tenant A, device 1 writes an object on partition "shared" and uploads it.
//   2. Tenant A, device 2 opens the SAME partition and must download that object
//      -> proves sync works within a tenant.
//   3. Tenant B, device 1 opens the SAME partition name and must see NOTHING
//      -> proves total isolation between tenants (same partition string, but the
//         server namespaces it under the tenant taken from the signed token).
//
// Each client authenticates with a per-tenant JWT minted by run_e2e.sh and
// signed with that tenant's private key.

#include <barq_native/sdk.hpp>

#include <chrono>
#include <fstream>
#include <future>
#include <iostream>
#include <sstream>
#include <string>

namespace barq::native {

struct Widget {
    primary_key<int64_t> _id;
    std::string name;
    int64_t value = 0;
};
BARQ_SCHEMA(Widget, _id, name, value)

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

// Opens a synced database for one (tenant, device) against the given route and
// partition. The tenant identity is carried by the signed token; `tenant_id`
// here is only used for the SDK's local file bookkeeping.
db open_synced(const std::string& tenant_id, const std::string& user_id, const std::string& token,
               const std::string& route, const std::string& partition, const std::string& path)
{
    sync_user user(tenant_id, user_id, token);
    user.set_route(route);
    sync_config sc = user.make_sync_config(partition);

    db_config cfg;
    cfg.set_path(path);
    cfg.set_sync_config(sc);
    return db(std::move(cfg));
}

// Waits on a completion future with a hard timeout so a broken sync can never
// hang the example. Returns false on timeout or error.
bool wait_ready(std::future<void> fut, int seconds, const std::string& label)
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

} // namespace

int main(int argc, char** argv)
{
    using namespace barq::native;

    if (argc < 4) {
        std::cerr << "usage: barq_mt_e2e <server_ws_url> <tokens_dir> <work_dir>\n";
        return 2;
    }
    const std::string route = argv[1];
    const std::string tokens_dir = argv[2];
    const std::string work_dir = argv[3];
    const int kTimeout = 30;

    std::string tok_a1, tok_a2, tok_b1;
    try {
        tok_a1 = read_token(tokens_dir + "/a1.jwt");
        tok_a2 = read_token(tokens_dir + "/a2.jwt");
        tok_b1 = read_token(tokens_dir + "/b1.jwt");
    }
    catch (const std::exception& e) {
        std::cerr << "fatal: " << e.what() << "\n";
        return 2;
    }

    std::cout << "Barq multi-tenancy e2e — route=" << route << "\n\n";

    // --- Step 1: tenant A / device 1 writes and uploads on partition "shared".
    std::cout << "[1] tenant A / device 1 writes on partition 'shared'\n";
    try {
        db a1 = open_synced("tenant-a", "device-1", tok_a1, route, "shared", work_dir + "/a1.barq");
        a1.write([&] {
            Widget w;
            w._id = 1;
            w.name = "from-tenant-A";
            w.value = 100;
            return a1.add(std::move(w));
        });
        auto session = a1.get_sync_session();
        check(bool(session), "tenant A device 1 has a sync session");
        bool uploaded = session && wait_ready(session->wait_for_upload_completion(), kTimeout, "A1 upload");
        check(uploaded, "tenant A device 1 uploaded its write");
    }
    catch (const std::exception& e) {
        std::cerr << "  ....  exception: " << e.what() << "\n";
        check(false, "tenant A device 1 upload (threw)");
    }

    // --- Step 2: tenant A / device 2 must see device 1's object (sync works).
    std::cout << "\n[2] tenant A / device 2 downloads the same partition\n";
    try {
        db a2 = open_synced("tenant-a", "device-2", tok_a2, route, "shared", work_dir + "/a2.barq");
        auto session = a2.get_sync_session();
        bool downloaded = session && wait_ready(session->wait_for_download_completion(), kTimeout, "A2 download");
        check(downloaded, "tenant A device 2 completed download");
        a2.refresh();
        auto widgets = a2.objects<Widget>();
        check(widgets.size() == 1, "tenant A device 2 sees exactly 1 widget (synced from device 1)");
        if (widgets.size() >= 1) {
            auto w = widgets[0];
            check(std::string(w.name) == "from-tenant-A", "the widget is the one device 1 wrote");
        }
    }
    catch (const std::exception& e) {
        std::cerr << "  ....  exception: " << e.what() << "\n";
        check(false, "tenant A device 2 download (threw)");
    }

    // --- Step 3: tenant B / device 1 on the SAME partition name must see nothing.
    std::cout << "\n[3] tenant B / device 1 opens partition 'shared' (isolation)\n";
    try {
        db b1 = open_synced("tenant-b", "device-1", tok_b1, route, "shared", work_dir + "/b1.barq");
        auto session = b1.get_sync_session();
        bool downloaded = session && wait_ready(session->wait_for_download_completion(), kTimeout, "B1 download");
        check(downloaded, "tenant B device 1 completed download");
        b1.refresh();
        auto widgets = b1.objects<Widget>();
        check(widgets.size() == 0, "tenant B sees 0 widgets — tenant A's data is NOT visible");
    }
    catch (const std::exception& e) {
        std::cerr << "  ....  exception: " << e.what() << "\n";
        check(false, "tenant B device 1 download (threw)");
    }

    std::cout << "\n" << (g_failures == 0 ? "ALL CHECKS PASSED" : "FAILURES: " + std::to_string(g_failures)) << "\n";
    return g_failures == 0 ? 0 : 1;
}
