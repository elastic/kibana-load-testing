import { Config } from './types/config'
import { loadSampleData } from './helpers/api'
import { Request } from './types/request'
import { getDuplicates } from './helpers/requestParser'
import { runner } from './puppeteer/runner'
import { saveResults } from './helpers/results';

export async function scenarioRunner(options: Config) {
    await loadSampleData(options);
    let scenarioResponses: Map<string, Map<string, Request>> = new Map();
    const scenarios = ['demoJourney']

    scenarioResponses = await runner(scenarios, options);

    scenarios.forEach(scn => {
        const frameRequests = scenarioResponses.get(scn)
        if (frameRequests) {
            saveResults(scn, frameRequests, options.baseUrl)
            getDuplicates(frameRequests, options.baseUrl)
        }
    })
}