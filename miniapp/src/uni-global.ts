// mp-weixin 运行时只暴露 globalThis.wx，uni API 对象仅作为模块导出存在，
// 因此这里把编译器重写后的 uni 实例挂回全局，供源码中的 globalThis.uni 使用。
// 必须在其它模块之前导入，保证 stores/api 在模块初始化阶段就能拿到 uni。
const globalScope = globalThis as { uni?: unknown }
if (!globalScope.uni) globalScope.uni = uni
