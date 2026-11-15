# Server

Protocol: [../docs/protocol.md](../docs/protocol.md).

## Build

```sh
cmake -S . -B build
cmake --build build
```

Cần CMake 3.15+ và một trình biên dịch C++17, trên Linux hoặc macOS. PostgreSQL là tùy chọn: nếu máy có libpq (`sudo apt install libpq-dev`) thì server được build kèm phần lưu bằng PostgreSQL.

## Chạy

```sh
./build/server            # port mặc định 5000
./build/server 6000
```

| Biến môi trường | Ý nghĩa | Mặc định |
|---|---|---|
| `NETPRO_USERS_FILE` | File lưu tài khoản và Elo | `users.db` |
| `NETPRO_LOG_FILE` | File log message nhận và gửi (log cũng in ra màn hình) | `server.log` |
| `PG_CONNINFO` | Nếu được đặt và server build kèm libpq: lưu bằng PostgreSQL thay cho file, ví dụ `host=localhost dbname=netpro user=netpro password=netpro` | không đặt |

## Dùng PostgreSQL (tùy chọn)

Không có PostgreSQL thì server lưu bằng file `users.db`, không cần làm gì thêm. Muốn dùng PostgreSQL:

1. Cài thư viện libpq để build: macOS `brew install libpq`; Ubuntu `sudo apt install libpq-dev`.
2. Build lại. Trên macOS phải chỉ chỗ Homebrew đặt libpq:

   ```sh
   cmake -S . -B build -DCMAKE_PREFIX_PATH=/opt/homebrew/opt/libpq   # Ubuntu: bỏ -D...
   cmake --build build
   ```

   Dòng `PostgreSQL found: building PgUserStore` trong lúc cmake chạy là thành công.
3. Bật database bằng Docker (cần Docker Desktop đang chạy), trong thư mục `server/`:

   ```sh
   docker compose up -d      # tắt: docker compose down (dữ liệu vẫn còn)
   ```

4. Chạy server với chuỗi kết nối:

   ```sh
   PG_CONNINFO="host=localhost port=5432 dbname=netpro user=netpro password=netpro" ./build/server
   ```

   Màn hình in `Storage: PostgreSQL`. Bỏ `PG_CONNINFO` thì server quay về lưu bằng file.

Xem dữ liệu bằng extension PostgreSQL của VS Code: host `localhost`, port `5432`, database `netpro`, user `netpro`, password `netpro`, bảng `users` (cột `elo_caro`, `elo_reversi`).

Bảng cũ (chỉ có cột `elo`) được tự thêm hai cột mới, mỗi game bắt đầu lại từ 1000; cột `elo` cũ để nguyên, không dùng.

## Test

```sh
ctest --test-dir build --output-on-failure
```

`tests/protocol_test.cpp` chạy các phiên mẫu của tài liệu protocol thẳng vào `core::Hub`, không cần socket.

## Client

```sh
cd ../client
sh mvnw javafx:run
```

Địa chỉ server nhập ở góc dưới bên trái màn hình đăng nhập, dạng `host:port`, rồi nhấn Enter.
