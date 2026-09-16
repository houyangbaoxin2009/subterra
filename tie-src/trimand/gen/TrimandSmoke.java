import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * TrimandSmoke —— Trimand DLL FFM 冒烟（纯 JDK，不依赖引擎/MC）。
 * 验证：DLL 可装载、导出符号 rfwd$coarse_*（climate/elev/mountain）可寻址、
 *  (i64,i64,i64)->f64 调用确定性与值域（与 tiec 侧 stdens 检查对拍）。
 * 运行（tie-src/trimand 下）：java --enable-native-access=ALL-UNNAMED -cp gen TrimandSmoke
 */
public class TrimandSmoke {
    public static void main(String[] args) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup lib = SymbolLookup.libraryLookup(Path.of("gen", "subterra_diffuse.dll"), arena);
            Linker linker = Linker.nativeLinker();
            FunctionDescriptor fd = FunctionDescriptor.of(
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
            String[] syms = {"rfwd$coarse_climate", "rfwd$coarse_elev", "rfwd$coarse_mountain"};
            boolean ok = true;
            for (String sym : syms) {
                MethodHandle mh = linker.downcallHandle(lib.find(sym).orElseThrow(), fd);
                double a = (double) mh.invoke(100L, 200L, 20260915L);
                double b = (double) mh.invoke(5120L, 8192L, 20260915L);
                double c = (double) mh.invoke(100L, 200L, 20260915L);
                boolean det = (a == c);
                boolean rng = a >= -1.5 && a <= 1.5 && b >= -1.5 && b <= 1.5;
                ok = ok && det && rng;
                System.out.println(sym + ": (100,200)=" + a + " (5120,8192)=" + b + " det=" + det + " range=" + rng);
            }
            System.out.println(ok ? "TRIMAND FFM SMOKE PASS" : "TRIMAND FFM SMOKE FAIL");
            if (!ok) {
                System.exit(1);
            }
        }
    }
}