import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 短链服务压测探针 —— 单文件、零依赖，JDK 11+ 可直接 `java bench/LoadGen.java ...` 运行。
 *
 * <p>为什么不用 JMeter / wrk / ab：本机没装，而且"测法本身会改变结论"（W5 的 R77）——
 * Windows 下 fork 一个 curl 要 ~150ms，根本打不出真并发。要打真并发，只能在 Java 里开线程。
 *
 * <p>用法：
 * <pre>
 *   java bench/LoadGen.java --url "http://localhost:8080/%s" --codes bench/codes.txt \
 *        --concurrency 100 --mode once --label "B1-cold"
 *   java bench/LoadGen.java --url "http://localhost:8080/%s" --codes bench/codes.txt \
 *        --concurrency 100 --mode loop --duration 30 --warmup 5 --label "B3-warm-sustained"
 * </pre>
 *
 * <p>mode：
 * <ul>
 *   <li>{@code once} —— 列表里每个短码只打一次，打空即停。用来测"冷路径"（全部缓存未命中 → 回源 MySQL）。</li>
 *   <li>{@code loop} —— 按列表循环打，跑满 duration 秒。用来测"热路径"（缓存命中）。</li>
 * </ul>
 *
 * <p>两个必须注意的点：
 * <ol>
 *   <li>{@code followRedirects(NEVER)} —— 跳转接口返回 302，默认会去追目标站点，那就不是压自己的服务了。</li>
 *   <li>{@code warmup} —— JIT 未预热时前几秒的延迟虚高，必须丢掉（跟 W5「降级耗时要用热连接」同一个道理）。</li>
 * </ol>
 */
public class LoadGen {

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parseArgs(args);

        String urlTemplate = a.getOrDefault("url", "http://localhost:8080/%s");
        Path codesPath = Path.of(a.getOrDefault("codes", "bench/codes.txt"));
        int concurrency = Integer.parseInt(a.getOrDefault("concurrency", "100"));
        String mode = a.getOrDefault("mode", "once");
        int durationSec = Integer.parseInt(a.getOrDefault("duration", "30"));
        int warmupSec = Integer.parseInt(a.getOrDefault("warmup", "0"));
        String label = a.getOrDefault("label", mode);

        List<String> codes = new ArrayList<>();
        for (String line : Files.readAllLines(codesPath)) {
            String s = line.trim();
            if (!s.isEmpty()) {
                codes.add(s);
            }
        }
        if (codes.isEmpty()) {
            throw new IllegalStateException("codes 文件为空: " + codesPath);
        }

        // ★ 线程必须是 daemon：HttpClient 的 executor 不会自动关闭，非守护线程会让 JVM 挂住不退出
        ExecutorService clientPool = Executors.newFixedThreadPool(concurrency, r -> {
            Thread t = new Thread(r, "loadgen-io");
            t.setDaemon(true);
            return t;
        });
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)      // ★ 302 必须原地停下
                .connectTimeout(Duration.ofSeconds(5))
                .executor(clientPool)
                .build();

        AtomicInteger cursor = new AtomicInteger();
        Map<Integer, LongAdder> statusCount = new ConcurrentHashMap<>();
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);
        List<Future<LongBuf>> futures = new ArrayList<>();

        final long t0 = System.nanoTime();
        final long warmupNanos = TimeUnit.SECONDS.toNanos(warmupSec);
        final long limitNanos = TimeUnit.SECONDS.toNanos(durationSec);
        final boolean loopMode = "loop".equalsIgnoreCase(mode);

        for (int w = 0; w < concurrency; w++) {
            futures.add(workers.submit(() -> {
                LongBuf latencies = new LongBuf(1 << 14);
                int n = codes.size();
                while (true) {
                    long elapsed = System.nanoTime() - t0;
                    if (loopMode) {
                        if (elapsed >= limitNanos) {
                            break;
                        }
                    }
                    int idx = cursor.getAndIncrement();
                    if (!loopMode && idx >= n) {
                        break;
                    }
                    String code = codes.get(idx % n);

                    long s = System.nanoTime();
                    int status;
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(urlTemplate, code)))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build();
                        status = client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
                    } catch (Exception e) {
                        status = -1;                              // -1 = 连接层失败（不是 HTTP 状态码）
                    }
                    long cost = System.nanoTime() - s;

                    long now = System.nanoTime() - t0;
                    if (now >= warmupNanos) {                     // 预热期内不记样本
                        latencies.add(cost);
                        statusCount.computeIfAbsent(status, k -> new LongAdder()).increment();
                    }
                }
                return latencies;
            }));
        }

        long[] all = new long[0];
        long totalRecorded = 0;
        for (Future<LongBuf> f : futures) {
            long[] part = f.get().toArray();
            totalRecorded += part.length;
            long[] merged = new long[all.length + part.length];
            System.arraycopy(all, 0, merged, 0, all.length);
            System.arraycopy(part, 0, merged, all.length, part.length);
            all = merged;
        }
        workers.shutdown();

        double elapsedTotal = (System.nanoTime() - t0) / 1e9;
        double measured = Math.max(elapsedTotal - warmupSec, 1e-9);

        System.out.println("==================== " + label + " ====================");
        System.out.printf("mode        : %s  (并发 %d, 码池 %d, warmup %ds)%n", mode, concurrency, codes.size(), warmupSec);
        System.out.printf("总耗时      : %.2f s（其中计入统计 %.2f s）%n", elapsedTotal, measured);
        System.out.printf("样本数      : %d%n", totalRecorded);
        System.out.printf("QPS         : %.1f%n", totalRecorded / measured);

        StringBuilder sb = new StringBuilder("状态码      : ");
        statusCount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue().sum()).append("  "));
        System.out.println(sb);

        if (all.length > 0) {
            java.util.Arrays.sort(all);
            System.out.printf("延迟(ms)    : p50=%.2f  p95=%.2f  p99=%.2f  max=%.2f%n",
                    pct(all, 50), pct(all, 95), pct(all, 99), all[all.length - 1] / 1e6);
        }
        System.out.println("========================================================");

        System.out.flush();
        System.exit(0);      // 兜底：确保 JVM 立刻退出，不留下悬挂的 IO 线程
    }

    private static double pct(long[] sorted, double p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(idx, 0)] / 1e6;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> a = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            a.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return a;
    }

    /** 可增长 long 数组：避免 Long 装箱带来的 GC 抖动污染延迟统计。 */
    private static final class LongBuf {
        private long[] buf;
        private int size;

        LongBuf(int cap) {
            buf = new long[cap];
        }

        void add(long v) {
            if (size == buf.length) {
                long[] bigger = new long[buf.length << 1];
                System.arraycopy(buf, 0, bigger, 0, size);
                buf = bigger;
            }
            buf[size++] = v;
        }

        long[] toArray() {
            long[] out = new long[size];
            System.arraycopy(buf, 0, out, 0, size);
            return out;
        }
    }
}
