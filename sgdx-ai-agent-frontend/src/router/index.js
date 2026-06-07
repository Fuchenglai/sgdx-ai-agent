import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - 韶关电信超级智能体应用平台',
      description: '韶关电信超级智能体应用平台提供AI高套助手和AI超级智能体服务，满足您的各种AI对话需求'
    }
  },
  {
    path: '/gt-master',
    name: 'GtMaster',
    component: () => import('../views/GtMaster.vue'),
    meta: {
      title: 'AI高套大师 - 韶关电信超级智能体应用平台',
      description: 'AI高套大师是韶关电信超级智能体应用平台的高套异常数据核查助手，帮你解答各种高套问题，提供精准建议和解决方案'
    }
  },
  {
    path: '/super-agent',
    name: 'SuperAgent',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'AI超级智能体 - 韶关电信超级智能体应用平台',
      description: 'AI超级智能体是韶关电信超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router 