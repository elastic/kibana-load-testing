import { Config } from './types/config'
import { loadSampleData } from './helpers/api'
import { Request } from './types/request'
import { getDuplicates } from './helpers/requestParser'
import { runner } from './puppeteer/runner'
import { saveResults } from './helpers/results';

export interface Scenario {
    name: string;
    updateRequired: boolean;
}

export async function scenarioRunner(options: Config, scenarios: string[]) {
    await loadSampleData(options);
    let scenarioResponses: Map<string, Map<string, Request>> = new Map();
    scenarioResponses = await runner(scenarios, options);
    let scenarioCheckList = new Array<Scenario>();

    scenarios.forEach(scn => {
        const frameRequests = scenarioResponses.get(scn)
        if (frameRequests) {
            const updateRequired = saveResults(scn, frameRequests, options.baseUrl)
            scenarioCheckList.push({ name: scn, updateRequired })
            getDuplicates(frameRequests, options.baseUrl)
        }
    })

    scenarioCheckList.forEach(scn => console.log(`Scenario ${scn.name} ${scn.updateRequired ? 'requires update' : 'is up-to-date'}`))

    if (options.scenarioCheck && scenarioCheckList.filter(scn => scn.updateRequired).length > 0) {
        throw new Error('Some scenarios require update!!!')
    }
}