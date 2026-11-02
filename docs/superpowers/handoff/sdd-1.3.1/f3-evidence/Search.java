import java.text.Normalizer;
import java.util.*;

/** Random search for texts where the literal N-02 design (NFC, no Cf, code points) is STRONGER than today's estimate. */
public class Search {
    static String gen(Random r, int[] alphabet, int maxLen) {
        StringBuilder sb = new StringBuilder();
        int n = 1 + r.nextInt(maxLen);
        for (int i = 0; i < n; i++) sb.appendCodePoint(alphabet[r.nextInt(alphabet.length)]);
        return sb.toString();
    }
    static int[] range(int a, int b) { int[] x = new int[b - a + 1]; for (int i = 0; i < x.length; i++) x[i] = a + i; return x; }
    static int[] concat(int[]... xs) { return Arrays.stream(xs).flatMapToInt(Arrays::stream).toArray(); }
    static int[] nfd(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).codePoints().toArray(); }

    public static void main(String[] a) {
        // (1) normal: ASCII printable, Spanish letters in NFC, some BMP symbols.
        int[] normal = concat(range(0x20, 0x7E), "áéíóúüñÁÉÍÓÚÜÑ¿¡€ºª·çÇ".codePoints().toArray());
        // (2) the task's generator: the normal ones plus emojis, Spanish letters in NFD and Cf characters.
        int[] cf = {0x00AD, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x2060, 0xFEFF, 0x202E, 0x2066, 0xE0041};
        int[] emoji = {0x1F600, 0x1F601, 0x1F602, 0x1F44D, 0x1F680, 0x2764, 0x1F512};
        int[] spanishNfd = nfd("áéíóúüñÁÉÍÓÚÜÑ");
        int[] task = concat(normal, cf, emoji, spanishNfd, range('a', 'z'), range('0', '9'));
        // (3) any assigned code point.
        Random r = new Random(20261101L);
        int[][] alphabets = {normal, task};
        String[] names = {"normal (NFC, BMP, no Cf)", "task generator (emoji, NFD, Cf)"};
        for (int k = 0; k < alphabets.length; k++) {
            int equal = 0, stronger = 0, n = 200_000; String ex = null;
            for (int i = 0; i < n; i++) {
                String s = gen(r, alphabets[k], 24);
                double o = Probe.oldBits(s), w = Probe.newBits(s);
                if (Math.abs(o - w) < 1e-9) equal++;
                if (w > o + 1e-9) { stronger++; if (ex == null) ex = s; }
            }
            System.out.printf("%-34s n=%d equal=%d stronger=%d%s%n", names[k], n, equal, stronger, ex == null ? "" : " e.g. " + hex(ex));
        }
        // Any code point (BMP and astral, assigned, not surrogates or private use).
        int stronger = 0, n = 200_000, longer = 0; Map<String, Integer> kinds = new TreeMap<>(); String ex = null;
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + r.nextInt(16);
            while (sb.codePointCount(0, sb.length()) < len) {
                int cp = r.nextInt(0x30000);
                int t = Character.getType(cp);
                if (t == Character.UNASSIGNED || t == Character.SURROGATE || t == Character.PRIVATE_USE) continue;
                sb.appendCodePoint(r.nextInt(3) == 0 ? 'a' + r.nextInt(26) : cp);
            }
            String s = sb.toString();
            if (Probe.newBits(s) > Probe.oldBits(s) + 1e-9) { stronger++; if (ex == null) ex = s; }
            if (Probe.nfcNoCfCps(s) > s.length()) longer++;
        }
        System.out.printf("%-34s n=%d stronger=%d nfcCodePoints>utf16Units=%d e.g. %s%n", "any assigned code point", n, stronger, longer, ex == null ? "-" : hex(ex));
    }
    static String hex(String s) { StringBuilder b = new StringBuilder(); s.codePoints().forEach(c -> b.append(String.format("U+%04X ", c))); return b.toString().trim(); }
}
