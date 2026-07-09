#include <barq_native/sdk.hpp>

#include <chrono>
#include <filesystem>
#include <iostream>
#include <map>
#include <optional>
#include <set>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace barq::native {

struct DemoAddress {
    std::string city;
    std::string street;
};
BARQ_EMBEDDED_SCHEMA(DemoAddress, city, street)

struct DemoPet;

struct DemoPerson {
    primary_key<int64_t> _id;
    std::string name;
    int64_t age = 0;
    bool active = true;
    std::optional<std::string> email;
    std::chrono::time_point<std::chrono::system_clock> created_at;
    uuid external_id;
    object_id token;
    decimal128 balance;
    std::vector<uint8_t> avatar;
    mixed note;
    DemoPet* favorite_pet = nullptr;
    DemoAddress* address = nullptr;
    std::vector<std::string> tags;
    std::set<std::string> skills;
    std::map<std::string, std::string> settings;
};
BARQ_SCHEMA(DemoPerson,
            _id,
            name,
            age,
            active,
            email,
            created_at,
            external_id,
            token,
            balance,
            avatar,
            note,
            favorite_pet,
            address,
            tags,
            skills,
            settings)

struct DemoPet {
    primary_key<int64_t> _id;
    std::string name;
    std::string species;
    linking_objects<&DemoPerson::favorite_pet> owners;
};
BARQ_SCHEMA(DemoPet, _id, name, species, owners)

DemoPerson make_person(int64_t id,
                       std::string name,
                       int64_t age,
                       DemoPet* favorite_pet,
                       DemoAddress* address)
{
    DemoPerson person;
    person._id = id;
    person.name = std::move(name);
    person.age = age;
    person.active = true;
    person.email = person.name + "@example.test";
    person.created_at = std::chrono::system_clock::now();
    person.external_id = uuid();
    person.token = object_id();
    person.balance = decimal128("108.50");
    person.avatar = {1, 2, 3, 4};
    person.note = mixed(std::string("created from demo"));
    person.favorite_pet = favorite_pet;
    person.address = address;
    person.tags = {"local", "offline", "demo"};
    person.skills = {"cpp", "queries", "objects"};
    person.settings = {{"theme", "dark"}, {"notifications", "on"}};
    return person;
}

} // namespace barq::native

namespace demo {

void remove_demo_files(const std::filesystem::path& path)
{
    std::filesystem::remove(path);
    std::filesystem::remove(path.string() + ".lock");
    std::filesystem::remove(path.string() + ".note");
    std::filesystem::remove(path.string() + ".control");
}

} // namespace demo

int main()
{
    using namespace demo;
    using namespace barq::native;

    const auto path = std::filesystem::current_path() / "barq-native-demo.barq";
    remove_demo_files(path);

    db_config config;
    config.set_path(path.string());

    auto database = db(std::move(config));

    DemoPet rex;
    rex._id = 1;
    rex.name = "Rex";
    rex.species = "dog";

    DemoAddress ada_address;
    ada_address.city = "Kuala Lumpur";
    ada_address.street = "Jalan Demo";

    auto ada = make_person(1, "Ada", 31, &rex, &ada_address);

    auto managed_person = database.write([&] {
        return database.add(std::move(ada));
    });

    std::cout << "created: " << std::string(managed_person.name)
              << ", age " << static_cast<int64_t>(managed_person.age) << "\n";

    database.write([&] {
        managed_person.age = 32;
        managed_person.tags.push_back("updated");
        managed_person.skills.insert("transactions");
        managed_person.settings["theme"] = "light";
        managed_person.note = std::string("updated in a write transaction");
    });

    auto adults = database.objects<DemoPerson>().where([](auto& person) {
        return person.age >= 18;
    });
    auto sorted_adults = adults.sort("name", true);

    std::cout << "adults found: " << sorted_adults.size() << "\n";
    for (auto person : sorted_adults) {
        std::cout << "query row: " << std::string(person.name)
                  << ", tags " << person.tags.size()
                  << ", skills " << person.skills.size()
                  << ", theme " << *person.settings["theme"] << "\n";
    }

    auto pet = database.objects<DemoPet>()[0];
    std::cout << "backlinks for " << std::string(pet.name)
              << ": " << pet.owners.size() << "\n";

    auto frozen_people = database.objects<DemoPerson>().freeze();
    std::cout << "frozen results: " << frozen_people.size()
              << ", is frozen " << (frozen_people.is_frozen() ? "yes" : "no") << "\n";

    auto reference = thread_safe_reference<DemoPerson>(managed_person);
    std::thread worker([path, reference = std::move(reference)]() mutable {
        db_config worker_config;
        worker_config.set_path(path.string());
        auto worker_database = db(std::move(worker_config));
        auto resolved = worker_database.resolve(std::move(reference));
        std::cout << "thread-safe reference: " << std::string(resolved.name) << "\n";
    });
    worker.join();

    DemoPerson temp;
    temp._id = 2;
    temp.name = "Temp";
    temp.age = 1;

    auto temp_object = database.write([&] {
        return database.add(std::move(temp));
    });
    database.write([&] {
        database.remove(temp_object);
    });

    std::cout << "people after delete: " << database.objects<DemoPerson>().size() << "\n";
    std::cout << "database file: " << path << "\n";

    return 0;
}
