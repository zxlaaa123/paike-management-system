<script setup lang="ts">
import {
  Calendar,
  Check,
  DataAnalysis,
  Finished,
  Lock,
  Monitor,
  Operation,
  School,
  Tickets,
  TrendCharts,
} from '@element-plus/icons-vue'

const metrics = [
  { label: '完成阶段', value: 'V1-V7', note: '主体开发与最终验收已收口' },
  { label: '项目完成度', value: '90%-95%', note: '准交付版，可用于演示与答辩' },
  { label: '验收用例', value: '52 passed', note: '最终 E2E 验收全量通过' },
  { label: '核心模块', value: '7 组', note: '基础数据、排课、分析、修复、治理闭环' },
]

const modules = [
  {
    icon: School,
    title: '基础数据管理',
    text: '维护教师、班级、课程、教室、学期和教学任务，为排课提供统一数据底座。',
  },
  {
    icon: Calendar,
    title: '智能排课流程',
    text: '支持手动排课、自动排课、多方案生成、方案对比和正式课表应用。',
  },
  {
    icon: DataAnalysis,
    title: '质量分析中心',
    text: '沉淀冲突报告、课表评分、教师工作量、教室利用率和班级均衡分析。',
  },
  {
    icon: Operation,
    title: '修复与重排闭环',
    text: '围绕冲突项创建修复任务，试算候选方案，完成局部调整和结果回归。',
  },
  {
    icon: Tickets,
    title: '系统治理能力',
    text: '覆盖审计日志、回归测试、一致性检查、性能基线、迁移状态和错误码中心。',
  },
  {
    icon: Lock,
    title: '安全与交付边界',
    text: '统一 Cookie 认证、CSRF 令牌、数据库幂等迁移和当前学期唯一性约束。',
  },
]

const milestones = [
  { version: 'V1-V2', title: '基础能力成型', detail: '完成系统框架、基础数据、教学任务与课表基础维护。' },
  { version: 'V3', title: '排课方案能力', detail: '多策略生成、方案管理、方案对比、统计分析进入主流程。' },
  { version: 'V4', title: '质量分析补齐', detail: '增加评分维度、风险中心、图表分析、锁定管理和报告能力。' },
  { version: 'V5', title: '修复流程闭环', detail: '支持智能修复建议、候选位置推荐、试算方案和局部重排。' },
  { version: 'V6', title: '系统治理收口', detail: '补齐审计、回归、一致性、性能、迁移和错误码治理页面。' },
  { version: 'V7', title: '最终验收版本', detail: '完成统计展示、首页治理摘要、性能趋势和 E2E Cookie 认证统一。' },
]

const techStack = ['Vue 3', 'TypeScript', 'Element Plus', 'Vite', 'Spring Boot', 'MyBatis Plus', 'MySQL', 'Playwright']

const acceptanceItems = [
  '后端测试通过',
  '前端类型检查通过',
  '前端构建通过',
  'V6 smoke 通过',
  'Stage6/Stage7/Stage9 E2E 通过',
  '最终隔离库验收 52 passed',
]
</script>

<template>
  <main class="showcase-page">
    <section class="hero-section">
      <div class="hero-copy">
        <el-tag class="version-tag" type="success" effect="plain">V7 Final / 准交付版</el-tag>
        <h1>高校排课管理系统</h1>
        <p>
          面向高校教务排课场景，覆盖基础数据、智能排课、质量分析、修复闭环和系统治理。
          当前主分支已完成 V1-V7 主体开发，具备课程设计、毕业设计和阶段性交付演示条件。
        </p>
        <div class="hero-actions">
          <el-button type="primary" size="large" tag="a" href="/login">进入系统</el-button>
          <el-button size="large" tag="a" href="#acceptance">查看验收</el-button>
        </div>
      </div>

      <div class="product-frame" aria-label="项目界面预览">
        <div class="browser-bar">
          <span></span>
          <span></span>
          <span></span>
          <strong>paike.local/dashboard</strong>
        </div>
        <img src="/showcase-dashboard.png" alt="高校排课管理系统首页界面" />
      </div>
    </section>

    <section class="metric-grid" aria-label="项目关键指标">
      <article v-for="item in metrics" :key="item.label" class="metric-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <p>{{ item.note }}</p>
      </article>
    </section>

    <section class="content-section">
      <div class="section-heading">
        <el-icon><Monitor /></el-icon>
        <div>
          <h2>功能版图</h2>
          <p>从数据准备到课表落地，再到质量治理和验收复盘，形成可演示的完整业务链路。</p>
        </div>
      </div>

      <div class="module-grid">
        <article v-for="item in modules" :key="item.title" class="module-card">
          <el-icon><component :is="item.icon" /></el-icon>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
        </article>
      </div>
    </section>

    <section class="content-section split-section">
      <div>
        <div class="section-heading compact">
          <el-icon><TrendCharts /></el-icon>
          <div>
            <h2>阶段路线</h2>
            <p>版本演进按“能排、能评、能修、能管、能验收”的顺序推进。</p>
          </div>
        </div>

        <div class="timeline">
          <article v-for="item in milestones" :key="item.version" class="timeline-item">
            <span>{{ item.version }}</span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.detail }}</p>
            </div>
          </article>
        </div>
      </div>

      <aside id="acceptance" class="acceptance-panel">
        <div class="panel-title">
          <el-icon><Finished /></el-icon>
          <h2>最终验收</h2>
        </div>
        <p class="acceptance-summary">
          最终验收记录显示：隔离库和默认库复验均可跑通，完整 E2E 结果为 52 passed。
        </p>
        <ul>
          <li v-for="item in acceptanceItems" :key="item">
            <el-icon><Check /></el-icon>
            <span>{{ item }}</span>
          </li>
        </ul>
      </aside>
    </section>

    <section class="content-section tech-section">
      <div class="section-heading compact">
        <el-icon><Tickets /></el-icon>
        <div>
          <h2>技术栈</h2>
          <p>前后端分离，配套自动化验收脚本和数据库迁移脚本，适合本地演示与继续部署整理。</p>
        </div>
      </div>
      <div class="tech-list">
        <el-tag v-for="item in techStack" :key="item" size="large" effect="plain">{{ item }}</el-tag>
      </div>
    </section>
  </main>
</template>

<style scoped>
.showcase-page {
  min-height: 100vh;
  background: #f5f7fa;
  color: #1f2d3d;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(320px, 0.78fr) minmax(420px, 1.22fr);
  gap: 40px;
  align-items: center;
  padding: 64px min(6vw, 88px) 32px;
  background:
    linear-gradient(135deg, rgba(35, 84, 136, 0.94), rgba(20, 35, 55, 0.96)),
    url('../../assets/hero.png') right 6vw center / 220px auto no-repeat;
  color: #fff;
}

.hero-copy {
  max-width: 620px;
}

.version-tag {
  margin-bottom: 18px;
  border-color: rgba(255, 255, 255, 0.42);
  background: rgba(255, 255, 255, 0.12);
  color: #e7f8ed;
}

.hero-copy h1 {
  margin: 0;
  font-size: 44px;
  line-height: 1.15;
  font-weight: 700;
  letter-spacing: 0;
}

.hero-copy p {
  margin: 20px 0 0;
  max-width: 580px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 17px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 30px;
}

.product-frame {
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 22px 54px rgba(0, 0, 0, 0.28);
}

.browser-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid #e4e7ed;
  background: #f8fafc;
}

.browser-bar span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #d7dde6;
}

.browser-bar strong {
  margin-left: 8px;
  color: #7b8794;
  font-size: 12px;
  font-weight: 500;
}

.product-frame img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  object-position: left top;
}

.metric-grid,
.content-section {
  width: min(1180px, calc(100% - 48px));
  margin: 0 auto;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: 28px 0;
}

.metric-card,
.module-card,
.acceptance-panel,
.tech-section {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
}

.metric-card {
  padding: 20px;
}

.metric-card span {
  color: #606266;
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  color: #1f2d3d;
  font-size: 28px;
  line-height: 1.2;
}

.metric-card p {
  margin: 8px 0 0;
  color: #7b8794;
  font-size: 13px;
  line-height: 1.55;
}

.content-section {
  padding: 22px 0 34px;
}

.section-heading {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-heading .el-icon,
.panel-title .el-icon {
  display: inline-flex;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #e8f3ff;
  color: #2f6c8f;
  font-size: 18px;
}

.section-heading h2,
.panel-title h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.3;
}

.section-heading p {
  margin: 6px 0 0;
  color: #606266;
  line-height: 1.7;
}

.compact {
  margin-bottom: 16px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  padding: 22px;
}

.module-card .el-icon {
  color: #b85c38;
  font-size: 24px;
}

.module-card h3 {
  margin: 14px 0 8px;
  font-size: 17px;
}

.module-card p,
.timeline-item p,
.acceptance-summary {
  margin: 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.7;
}

.split-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: start;
}

.timeline {
  display: grid;
  gap: 12px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  padding: 16px 18px;
  border-left: 3px solid #2f6c8f;
  background: #fff;
}

.timeline-item span {
  color: #2f6c8f;
  font-weight: 700;
}

.timeline-item h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.acceptance-panel {
  position: sticky;
  top: 24px;
  padding: 22px;
}

.panel-title {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.acceptance-panel ul {
  display: grid;
  gap: 10px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
}

.acceptance-panel li {
  display: flex;
  gap: 8px;
  align-items: center;
  color: #1f2d3d;
  font-size: 14px;
}

.acceptance-panel li .el-icon {
  color: #67c23a;
}

.tech-section {
  padding: 24px;
  margin-bottom: 40px;
}

.tech-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 1024px) {
  .hero-section {
    grid-template-columns: 1fr;
    padding-top: 42px;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .module-grid,
  .split-section {
    grid-template-columns: 1fr;
  }

  .acceptance-panel {
    position: static;
  }
}

@media (max-width: 640px) {
  .hero-section {
    padding: 32px 20px 24px;
  }

  .hero-copy h1 {
    font-size: 34px;
  }

  .hero-copy p {
    font-size: 15px;
  }

  .metric-grid,
  .content-section {
    width: calc(100% - 32px);
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .timeline-item {
    grid-template-columns: 1fr;
  }
}
</style>
