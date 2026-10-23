import local_runtime as runtime


def main():
    password = runtime.settings()["root_password"]
    queries = [
        "SELECT UTC_TIMESTAMP(6),COUNT(*) FROM information_schema.INNODB_TRX",
        "SELECT trx_state,trx_started,trx_wait_started FROM information_schema.INNODB_TRX ORDER BY trx_started",
        "SELECT REQUESTING_ENGINE_TRANSACTION_ID,BLOCKING_ENGINE_TRANSACTION_ID FROM performance_schema.data_lock_waits",
        "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running')",
    ]
    for query in queries:
        print(runtime.mysql(query, password).stdout)


if __name__ == "__main__":
    main()
