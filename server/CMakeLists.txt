# server/CMakeLists.txt
cmake_minimum_required(VERSION 3.15)
project(netpro_server CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(PostgreSQL REQUIRED)   # cần: sudo apt install libpq-dev

add_executable(server
    src/main.cpp
    src/net/Server.cpp
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
    src/store/UserStore.cpp
)
target_include_directories(server PRIVATE src)
target_compile_options(server PRIVATE -Wall -Wextra)
target_link_libraries(server PRIVATE PostgreSQL::PostgreSQL)