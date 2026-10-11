import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 压测数据准备 —— 单文件、零依赖，`java bench/GenCodes.java ...` 运行。
 *
 * <p>两种模式：
 * <ul>
 *   <li>{@code create} —— 通过真实接口 `POST /api/link` 创建 N 条短链，抓回 shortCode 写进文件。
 *       走真实创建路径的好处：短码会自动进 MySQL + 布隆过滤器，跟线上状态一致。</li>
 *   <li>{@code random} —— 直接生成 N 个**不存在的**随机短码（Base62、8 位），
 *       用来测布隆过滤器对无效码的拦截效果。</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 *   java bench/GenCodes.java --mode create --count 3000 --out bench/codes.txt
 *   java bench/GenCodes.java --mode random --count 3000 --out bench/invalid.txt
 * </pre>
 */
public class GenCodes {

    private static final Pattern SHORT_CODE = Pattern.compile("\"shortCode\"\\s*:\\s*\"([^\"]+)\"");
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static void main(String[] args) throws Exception {
        String mode = arg(args, "mode", "create");
        int count = Integer.parseInt(arg(args, "count", "3000"));
        Path out = Path.of(arg(args, "out", "bench/codes.txt"));
        String endpoint = arg(args, "url", "http://localhost:8080/api/link");
        int concurrency = Integer.parseInt(arg(args, "concurrency", "32"));
        String prefix = arg(args, "prefix", "https://example.com/bench/");

        List<String> codes;
        if ("random".equalsIgnoreCase(mode)) {
            codes = randomCodes(count);
        } else {
            codes = createLinks(endpoint, prefix, count, concurrency);
        }

        Files.write(out, codes);
        System.out.printf("[GenCodes] mode=%s  count=%d  已写入 %s%n", mode, codes.size(), out.toAbsolutePath());
        System.out.println("[GenCodes] 前 5 个: " + codes.subList(0, Math.min(5, codes.size())));
    }

    /** 走真实接口创建短链，抓回 shortCode。 */
    private static List<String> createLinks(String endpoint, String prefix, int count, int concurrency) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                String json = "{\"originUrl\":\"" + prefix + n + "\"}";
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                try {
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    Matcher m = SHORT_CODE.matcher(resp.body());
                    if (m.find()) {
                        codes.add(m.group(1));
                    } else {
                        System.err.println("[GenCodes] 第 " + n + " 条没抓到 shortCode，HTTP " + resp.statusCode() + " body=" + resp.body());
                    }
                } catch (Exception e) {
                    System.err.println("[GenCodes] 第 " + n + " 条失败: " + e);
                }
                if (n % 500 == 0) {
                    System.out.println("[GenCodes] 已创建 " + n + " / " + count);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        if (codes.size() < count) {
            System.err.println("[GenCodes] ⚠️ 只成功 " + codes.size() + " / " + count + " 条");
        }
        return codes;
    }

    /** 生成 count 个不重复的 8 位 Base62 随机短码（几乎必然不在库中）。 */
    private static List<String> randomCodes(int count) {
        Random rnd = new Random(20261011L);          // 固定种子 → 可复现
        Set<String> set = new LinkedHashSet<>();
        while (set.size() < count) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
            }
            set.add(sb.toString());
        }
        return new ArrayList<>(set);
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--" + key)) {
                return args[i + 1];
            }
        }
        return def;
    }
}
