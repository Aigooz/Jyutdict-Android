package cc.ecisr.jyutdict.struct;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.HashMap;
import java.util.Map;

/** Administrative grouping for API labels that mix cities, counties and old names. */
final class LocationOrder {
    private static final Map<String, Group> GROUPS = new HashMap<>();

    static {
        register(1, 10, "江门", "江门", "蓬江", "江海", "台山", "新会", "鹤山", "开平", "恩平");
        register(1, 20, "广州", "广州");
        register(1, 30, "佛山", "佛山", "顺德", "南海");
        register(1, 40, "中山", "中山");
        register(1, 50, "东莞", "东莞");
        register(1, 60, "肇庆", "肇庆", "封川", "开建", "封开");
        register(1, 70, "云浮", "云浮", "新兴");
        register(1, 80, "阳江", "阳江", "阳春");
        register(1, 90, "茂名", "茂名", "化州", "高州", "信宜", "电白");
        register(1, 100, "湛江", "湛江", "吴川", "遂溪", "雷州", "廉江");
        register(2, 10, "梧州", "梧州", "苍梧", "蒙山");
        register(2, 20, "南宁", "南宁", "横州");
        register(2, 30, "贵港", "贵港", "桂平");
        register(2, 40, "玉林", "玉林", "北流", "容县", "陆川", "博白");
        register(2, 50, "北海", "北海", "廉州", "合浦");
        register(2, 60, "钦州", "钦州", "灵山");
        register(2, 70, "防城港", "防城港", "防城");
        register(2, 80, "百色", "百色");
        register(2, 90, "柳州", "柳州");
        register(3, 10, "郴州", "郴州", "宜章");
    }

    private LocationOrder() {}

    private static void register(int province, int city, String name, String... labels) {
        for (int index = 0; index < labels.length; index++) {
            GROUPS.put(labels[index], new Group(province, city, name, index));
        }
    }

    static String normalize(String value) {
        return value == null ? "" : ZhConverterUtil.toSimple(value.trim());
    }

    static boolean historical(LocationInfo.Location location) {
        return "历史音".equals(normalize(location.first));
    }

    static Group group(LocationInfo.Location location) {
        String label = administrativeLabel(location.second);
        Group group = GROUPS.get(label);
        return group == null ? new Group(99, 99, label, 0) : group;
    }

    static String administrativeLabel(String value) {
        String label = normalize(value);
        // Only remove an administrative suffix after exact lookup, not arbitrary prefixes.
        if (!GROUPS.containsKey(label)
                && (label.endsWith("市") || label.endsWith("区") || label.endsWith("县"))) {
            String shortLabel = label.substring(0, label.length() - 1);
            if (GROUPS.containsKey(shortLabel)) return shortLabel;
        }
        return label;
    }

    static final class Group {
        final int province;
        final int city;
        final String name;
        final int county;

        Group(int province, int city, String name, int county) {
            this.province = province;
            this.city = city;
            this.name = name;
            this.county = county;
        }
    }
}
