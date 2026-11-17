# server/CMakeLists.txt
cmake_minimum_required(VERSION 3.15)
project(netpro_server CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Phần lõi: protocol, game, lưu trữ bằng file. Không biết socket, nên test được trực tiếp.
add_library(netpro_core STATIC
    src/proto/Codec.cpp
    src/core/Hub.cpp
    src/core/Registry.cpp
    src/core/auth.cpp
    src/core/info.cpp
    src/core/entry.cpp
    src/core/play.cpp
    src/game/Gomoku.cpp
    src/game/Reversi.cpp
    src/game/GameFactory.cpp
    src/store/Elo.cpp
    src/store/FileUserStore.cpp
)
target_include_directories(netpro_core PUBLIC src)
target_compile_options(netpro_core PRIVATE -Wall -Wextra)

add_executable(server
    src/main.cpp
    src/net/Server.cpp
    src/util/Logger.cpp
)
target_compile_options(server PRIVATE -Wall -Wextra)
target_link_libraries(server PRIVATE netpro_core)

# PostgreSQL là tùy chọn: có libpq (sudo apt install libpq-dev) thì build kèm PgUserStore,
# và server dùng nó khi biến môi trường PG_CONNINFO được đặt. Không có thì lưu bằng file.
option(NETPRO_WITH_POSTGRES "Build the PostgreSQL user store when libpq is available" ON)
if(NETPRO_WITH_POSTGRES)
    find_package(PostgreSQL QUIET)
    if(PostgreSQL_FOUND)
        target_sources(server PRIVATE src/store/PgUserStore.cpp)
        target_compile_definitions(server PRIVATE NETPRO_HAVE_PG)
        target_link_libraries(server PRIVATE PostgreSQL::PostgreSQL)
        message(STATUS "PostgreSQL found: building PgUserStore")
    else()
        message(STATUS "PostgreSQL not found: file storage only")
    endif()
endif()

# Test chạy thẳng vào Hub (không cần socket):  ctest --test-dir build --output-on-failure
enable_testing()
add_executable(protocol_test tests/protocol_test.cpp)
target_compile_options(protocol_test PRIVATE -Wall -Wextra)
target_link_libraries(protocol_test PRIVATE netpro_core)
add_test(NAME protocol_test COMMAND protocol_test)
