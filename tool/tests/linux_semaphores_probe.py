"""Run in the Linux container with its session preload enabled."""
import multiprocessing as mp


def increment(lock, counter):
    for _ in range(200):
        with lock:
            counter.value += 1


if __name__ == "__main__":
    for method in ("fork", "spawn"):
        context = mp.get_context(method)
        lock = context.Lock()
        counter = context.Value("i", 0)
        children = [context.Process(target=increment, args=(lock, counter)) for _ in range(3)]
        for child in children:
            child.start()
        try:
            for child in children:
                child.join(8)
                assert child.exitcode == 0, (method, child.exitcode)
            assert counter.value == 600, counter.value
            print("PASS:", method, "shared semaphore synchronization", counter.value, flush=True)
        finally:
            for child in children:
                if child.is_alive():
                    child.kill()
                    child.join()
