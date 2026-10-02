type Step = { stepNo?: number; strategyType: number }
type PlanResponse = { planReady?: boolean; plan?: { planId?: string | number; parentPipeline?: { steps?: Step[] }; childPipeline?: { steps?: Step[] } } }

export function buildStrategyActions(documentId: string, response: PlanResponse) {
  const plan = response?.plan
  const parentSteps = (plan?.parentPipeline?.steps || []).map(({ stepNo, strategyType }) => ({ stepNo, strategyType }))
  const childSteps = (plan?.childPipeline?.steps || []).map(({ stepNo, strategyType }) => ({ stepNo, strategyType }))
  if (!documentId || !plan?.planId || !parentSteps.length || !childSteps.length) throw new Error('当前没有可确认的完整策略方案')
  return {
    confirm: { documentId, basePlanId: plan.planId, parentSteps, childSteps },
    build: { documentId, planId: plan.planId }
  }
}

export function isTerminalTask(task: { taskStatus?: number | string } | null | undefined): boolean {
  return [3, 4, 5].includes(Number(task?.taskStatus))
}
