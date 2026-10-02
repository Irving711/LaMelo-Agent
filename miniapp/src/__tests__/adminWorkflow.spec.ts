import { buildStrategyActions, isTerminalTask } from '../services/adminWorkflow'

describe('document administrator workflow', () => {
  it('uses the nested strategy plan returned by the backend', () => {
    const actions = buildStrategyActions('42', {
      planReady: true,
      plan: {
        planId: 9,
        parentPipeline: { steps: [{ stepNo: 1, strategyType: 2 }] },
        childPipeline: { steps: [{ stepNo: 1, strategyType: 3 }] }
      }
    })
    expect(actions.confirm).toEqual({ documentId: '42', basePlanId: 9, parentSteps: [{ stepNo: 1, strategyType: 2 }], childSteps: [{ stepNo: 1, strategyType: 3 }] })
    expect(actions.build).toEqual({ documentId: '42', planId: 9 })
  })

  it('recognizes all terminal task codes', () => {
    expect(isTerminalTask({ taskStatus: 3 })).toBe(true)
    expect(isTerminalTask({ taskStatus: 4 })).toBe(true)
    expect(isTerminalTask({ taskStatus: 5 })).toBe(true)
    expect(isTerminalTask({ taskStatus: 2 })).toBe(false)
  })
})
