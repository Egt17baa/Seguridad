import java.text.Normalizer;
import java.util.*;
public class Explain {
    public static void main(String[] a) {
        int[][] cases = {
            {0x29,0x301,0x55,0x38,0x53,0x75,0x71,0x56,0x50,0x49,0x75,0x59,0x303,0xDC,0xC9,0x4F,0x54,0x30,0x4E,0x49,0x52,0x4F,0xE1,0xF1},
            {0x0F76, 0x7A, 0x6D},
        };
        for (int[] c : cases) {
            String s = new String(c, 0, c.length);
            String n = Normalizer.normalize(s, Normalizer.Form.NFC);
            System.out.println("raw units=" + s.length() + " effLen(old)=" + Probe.effLen(s.chars().toArray()) + " old=" + String.format("%.1f", Probe.oldBits(s)));
            System.out.println("nfc cps=" + n.codePointCount(0, n.length()) + " effLen(new)=" + Probe.effLen(n.codePoints().toArray()) + " new=" + String.format("%.1f", Probe.newBits(s)));
            StringBuilder b = new StringBuilder(); n.codePoints().forEach(x -> b.append(String.format("U+%04X ", x))); System.out.println("nfc: " + b);
        }
        // Minimal greedy case: "a b X c d" where X merges away. Search short ASCII+combining strings.
        String[] t = {"zýxw", "ab́cd"};
        for (String s : t) System.out.println(Arrays.toString(s.chars().toArray()) + " old effLen=" + Probe.effLen(s.chars().toArray()) + " new effLen=" + Probe.effLen(Normalizer.normalize(s, Normalizer.Form.NFC).codePoints().toArray()));
    }
}
