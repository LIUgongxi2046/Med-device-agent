import { effectScope, watch } from 'vue';
/** Own only this write interval, including after component unmount. */
export function acquireSimulationRuleWrite(user: {
    medicalWriting: boolean;
}): () => void {
    if (user.medicalWriting)
        throw new Error('已有操作正在提交，请等待原操作结果');
    let owned = true;
    const scope = effectScope(true);
    scope.run(() => watch(() => user.medicalWriting, value => {
        if (!value)
            owned = false;
    }, { flush: 'sync' }));
    user.medicalWriting = true;
    return () => {
        if (owned) {
            owned = false;
            user.medicalWriting = false;
        }
        scope.stop();
    };
}
