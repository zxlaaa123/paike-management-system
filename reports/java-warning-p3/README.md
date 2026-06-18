# Java warning P3 cleanup artifacts

This directory keeps the pre-cleanup IDE warning export and analysis used for the Java warning P3 cleanup.

- `问题集合.json`: VS Code / redhat.java warning export, 92 warnings before cleanup.
- `Java警告分析报告.md`: warning classification and cleanup priority analysis.

Cleanup commit: `210fb9c chore: 清理 Java 警告 P3`.

Verification:

- `mvn -q test -DskipTests`: passed.
- Targeted P3 test set: passed.
- Full backend `mvn test`: passed with `DB_USERNAME=root`, `DB_PASSWORD=123456`, and explicit `JWT_SECRET`.
