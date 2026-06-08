package com.paike.scheduler.service;

import com.paike.scheduler.service.vo.MigrationInitializerStatusVo;
import com.paike.scheduler.service.vo.MigrationScriptStatusVo;
import com.paike.scheduler.service.vo.MigrationStatusOverviewVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DatabaseMigrationStatusService {

    private final String schemaLocations;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public DatabaseMigrationStatusService(
            @Value("${spring.sql.init.schema-locations:}") String schemaLocations
    ) {
        this.schemaLocations = schemaLocations == null ? "" : schemaLocations;
    }

    public MigrationStatusOverviewVo getStatus() {
        Map<String, Integer> configuredOrderByPath = parseConfiguredLocations();
        Map<String, Resource> resourcesByPath = loadSqlResources();
        Set<String> allPaths = new LinkedHashSet<>();
        allPaths.addAll(configuredOrderByPath.keySet());
        allPaths.addAll(resourcesByPath.keySet());

        List<MigrationScriptStatusVo> scripts = new ArrayList<>();
        for (String resourcePath : allPaths) {
            Resource resource = resourcesByPath.get(resourcePath);
            Integer order = configuredOrderByPath.get(resourcePath);
            scripts.add(buildScriptStatus(resourcePath, order, resource));
        }
        scripts.sort(Comparator
                .comparing((MigrationScriptStatusVo vo) -> vo.getConfiguredOrder() == null ? Integer.MAX_VALUE : vo.getConfiguredOrder())
                .thenComparing(MigrationScriptStatusVo::getScriptName));

        MigrationStatusOverviewVo overview = new MigrationStatusOverviewVo();
        overview.setMigrationTool("Spring SQL Init");
        overview.setScripts(scripts);
        overview.setInitializers(buildInitializers());
        overview.setTotalScriptCount(scripts.size());
        overview.setConfiguredScriptCount((int) scripts.stream().filter(MigrationScriptStatusVo::getConfigured).count());
        overview.setMissingScriptCount((int) scripts.stream()
                .filter(script -> Boolean.TRUE.equals(script.getConfigured()) && Boolean.FALSE.equals(script.getExistsOnClasspath()))
                .count());
        overview.setUnconfiguredScriptCount((int) scripts.stream()
                .filter(script -> Boolean.FALSE.equals(script.getConfigured()) && Boolean.TRUE.equals(script.getExistsOnClasspath()))
                .count());
        return overview;
    }

    private Map<String, Integer> parseConfiguredLocations() {
        Map<String, Integer> result = new LinkedHashMap<>();
        String[] locations = schemaLocations.split(",");
        for (int index = 0; index < locations.length; index++) {
            String normalized = normalizeLocation(locations[index]);
            if (!normalized.isEmpty()) {
                result.putIfAbsent(normalized, index + 1);
            }
        }
        return result;
    }

    private Map<String, Resource> loadSqlResources() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath*:db/*.sql");
            Map<String, Resource> result = new LinkedHashMap<>();
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
            for (Resource resource : sorted) {
                String filename = resource.getFilename();
                if (filename != null && !filename.isBlank()) {
                    result.put("db/" + filename, resource);
                }
            }
            return result;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private MigrationScriptStatusVo buildScriptStatus(String resourcePath, Integer configuredOrder, Resource resource) {
        MigrationScriptStatusVo vo = new MigrationScriptStatusVo();
        vo.setResourcePath(resourcePath);
        vo.setScriptName(resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
        vo.setConfiguredOrder(configuredOrder);
        vo.setConfigured(configuredOrder != null);
        vo.setExistsOnClasspath(resource != null && resource.exists());
        if (configuredOrder == null) {
            vo.setStatus("UNCONFIGURED");
        } else if (resource == null || !resource.exists()) {
            vo.setStatus("MISSING");
        } else {
            vo.setStatus("CONFIGURED");
        }
        applyRiskHint(vo, resource);
        return vo;
    }

    private void applyRiskHint(MigrationScriptStatusVo vo, Resource resource) {
        if (resource == null || !resource.exists()) {
            vo.setRiskLevel("UNKNOWN");
            vo.setIdempotentHint("脚本不在 classpath 中，无法分析。");
            return;
        }
        String sql = readResource(resource).toUpperCase(Locale.ROOT);
        if (sql.contains("DROP TABLE") || sql.contains("TRUNCATE TABLE")) {
            vo.setRiskLevel("HIGH");
            vo.setIdempotentHint("包含 DROP/TRUNCATE，需人工确认开发环境数据影响。");
        } else if (sql.contains("ALTER TABLE") || sql.contains("CREATE INDEX") || sql.contains("ADD INDEX")) {
            vo.setRiskLevel("MEDIUM");
            vo.setIdempotentHint("包含 ALTER/索引变更，需确认是否有条件保护或可重复执行。");
        } else if (sql.contains("CREATE TABLE IF NOT EXISTS") || sql.contains("INSERT IGNORE")) {
            vo.setRiskLevel("LOW");
            vo.setIdempotentHint("包含 IF NOT EXISTS 或 INSERT IGNORE，重复执行风险较低。");
        } else {
            vo.setRiskLevel("MEDIUM");
            vo.setIdempotentHint("未识别到明确幂等保护，需人工确认。");
        }
    }

    private String readResource(Resource resource) {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<MigrationInitializerStatusVo> buildInitializers() {
        List<MigrationInitializerStatusVo> result = new ArrayList<>();
        result.add(initializer(
                "默认管理员初始化",
                "CommandLineRunner",
                "com.paike.scheduler.config.AdminUserInitializer",
                "PRESENT",
                "启动时确保默认管理员账号存在。"));
        result.add(initializer(
                "学期结构兜底初始化",
                "CommandLineRunner",
                "com.paike.scheduler.config.SemesterSchemaInitializer",
                "PRESENT",
                "启动时兜底创建学期相关列和索引。"));
        return result;
    }

    private MigrationInitializerStatusVo initializer(String name, String type, String className,
                                                    String status, String description) {
        MigrationInitializerStatusVo vo = new MigrationInitializerStatusVo();
        vo.setName(name);
        vo.setType(type);
        vo.setClassName(className);
        vo.setStatus(status);
        vo.setDescription(description);
        return vo;
    }

    private String normalizeLocation(String location) {
        String value = location == null ? "" : location.trim();
        if (value.startsWith("classpath*:")) {
            value = value.substring("classpath*:".length());
        } else if (value.startsWith("classpath:")) {
            value = value.substring("classpath:".length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }
}
