package io.toterra.subterra.probes;

import io.toterra.subterra.engine.tie.TieFunction;
import io.toterra.subterra.engine.tie.TieLibrary;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * p.2.1 tie bridge 确定性探针：tiec 编译的动态库 → Java 25 FFM downcall（真实 ABI 调用链）。
 *
 * <p>自包含：从 devkit 资源提取捆绑的 tiefib_probe.dll（源 tie 见
 * resources/tie/tiefib_probe.tie，重建用 tiec --shared）。运行期 JavaExec 需
 * {@code --enable-native-access=ALL-UNNAMED}。
 *
 * <p>断言与 tie-main/examples/lib_math_dyn/main.c 同风格：正断言 7 项 + 负断言 1 项。
 */
public final class TieBridgeProbe {

    private TieBridgeProbe() {
    }

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. 提取捆绑资源 → 临时文件（libraryLookup 需要真实文件路径）。
        Path dll = Path.of(System.getProperty("java.io.tmpdir"),
                "subterra_tiefib_" + System.nanoTime() + ".dll");
        boolean extracted = false;
        try (InputStream in = TieBridgeProbe.class.getResourceAsStream("/tie/tiefib_probe.dll")) {
            extracted = in != null;
            check("捆绑资源 tiefib_probe.dll", extracted);
            if (!extracted) {
                finish();
                return;
            }
            Files.copy(in, dll, StandardCopyOption.REPLACE_EXISTING);
        }

        try (TieLibrary lib = TieLibrary.load(dll)) {
            check("库加载（libraryLookup）", true);

            // 导出面：6 个 pub 符号应存在，私有符号不应存在。
            check("符号 tiefib$add", lib.contains("tiefib$add"));
            check("符号 tiefib$mul", lib.contains("tiefib$mul"));
            check("符号 tiefib$neg", lib.contains("tiefib$neg"));
            check("符号 tiefib$half", lib.contains("tiefib$half"));
            check("符号 tiefib$use_secret", lib.contains("tiefib$use_secret"));
            check("私有 secret 未导出", !lib.contains("tiefib$secret"));

            // 调用断言（与 C 侧 main.c 数值一致）。
            TieFunction add = lib.find("tiefib$add").orElse(null);
            TieFunction mul = lib.find("tiefib$mul").orElse(null);
            TieFunction neg = lib.find("tiefib$neg").orElse(null);
            TieFunction half = lib.find("tiefib$half").orElse(null);
            TieFunction useSecret = lib.find("tiefib$use_secret").orElse(null);
            if (add == null || mul == null || neg == null || half == null || useSecret == null) {
                check("导出函数解析齐全", false);
                finish();
                return;
            }
            check("导出函数解析齐全", true);

            check("add(2,3)=5", add.invokeI64I64(2, 3) == 5L);
            check("mul(6,7)=42", mul.invokeI64I64(6, 7) == 42L);
            check("neg(7)=-7", neg.invokeI64(7) == -7L);
            check("half(9.0)=4.5", Math.abs(half.invokeF64(9.0) - 4.5) < 1e-9);
            check("use_secret(2)=1999", useSecret.invokeI64(2) == 1999L);
        } finally {
            Files.deleteIfExists(dll);
        }

        finish();
    }

    private static void finish() {
        if (failures == 0) {
            System.out.println("=== TieBridgeProbe ALL PASS（tiec→DLL→Java FFM 链路可行）===");
        } else {
            System.out.println("=== TieBridgeProbe " + failures + " 项失败 ===");
            System.exit(1);
        }
    }
}