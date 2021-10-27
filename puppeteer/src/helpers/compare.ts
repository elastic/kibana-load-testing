import fs from 'fs'
import { resolve } from 'path';

export function compareWithBaseline(scenario: string, actualSequence: Map<string, string[]>) {
    console.log(`${scenario} scenario: comparing recorded requestes with baseline:`);
    const repeatableCalls = [
        'POST /internal/bsearch?compress=true 200',
        'POST /api/saved_objects/_bulk_get 200',
        'POST /api/saved_objects/_bulk_resolve 200',
        'POST /api/canvas/fns?compress=true 200',
        'GET /api/canvas/workpad/resolve/workpad-e08b9bdb-ec14-4339-94c4-063bddfd610e 200', // https://github.com/elastic/kibana/issues/114340
        'POST /internal/global_search/find 200'
    ];
    let isUpdateRequired = false;
    let baselinePath = resolve(__dirname, '..', '..', 'baseline', scenario, 'requests.json');
    const expectedSequence = new Map(Object.entries(JSON.parse(fs.readFileSync(baselinePath, 'utf8')) as JSON)) as Map<string, string[]>;

    const verifiedSequence = new Map<string, string[]>();

    const newOnes = new Map<string, string[]>();
    const oldOnes = new Map<string, string[]>();

    actualSequence.forEach((actualUrls, path) => {
        const actualConst = [...actualUrls];
        let arr = Array<string>();
        let newReqs = Array<string>();
        let notFoundReqs = Array<string>();
        if (expectedSequence.has(path)) {
            let expectedUrls = expectedSequence.get(path) || []
            const expectedConst = [...expectedUrls];

            while ((expectedUrls.length > 0) && (actualUrls.length > 0)) {
                if (actualUrls[0] === expectedUrls[0]) {
                    arr.push(`${actualUrls[0]} - ok`)
                    actualUrls.shift();
                    expectedUrls.shift();
                } else {
                    if (expectedUrls.indexOf(actualUrls[0]) == -1) {
                        // could be extra call of an existing one
                        if (repeatableCalls.includes(actualUrls[0]) && actualConst.filter(i => i === actualUrls[0]).length > 1) {
                            console.log(`extra '${path} ${actualUrls[0]}' is found, but was not in baseline`)
                        } else {
                            // new request
                            arr.push(`${actualUrls[0]} - new request`);
                            newReqs.push(actualUrls[0]);
                            isUpdateRequired = true;
                        }
                        actualUrls.shift();
                    // request not found
                    } else if (actualUrls.indexOf(expectedUrls[0]) == -1) {
                        arr.push(`${expectedUrls[0]} - not found`);
                        notFoundReqs.push(expectedUrls[0]);
                        expectedUrls.shift();
                    // change order
                    } else if (expectedUrls.indexOf(actualUrls[0]) > -1) {
                        arr.push(`${actualUrls[0]} - changed order`);
                        expectedUrls.splice(expectedUrls.indexOf(actualUrls[0]), 1);
                        actualUrls.shift();
                    } else {
                        console.log("smth went wrong")
                    }
                }
            }
            if (expectedUrls.length > 0) {
                expectedUrls.map(el => {
                    if (repeatableCalls.includes(el) && expectedConst.filter(i => i === el).length > 0) {
                        console.log(`extra '${path} ${el}' is not found, but was in baseline`);
                    } else {
                        arr.push(`${el} - not found`)
                        isUpdateRequired = true
                        notFoundReqs.push(el)
                    }
                })
            }
            if (actualUrls.length > 0) {
                actualUrls.map(el => {
                    if (repeatableCalls.includes(el) && actualConst.filter(i => i === el).length > 0) {
                        console.log(`extra '${path} ${el}' is found, but was not in baseline`);
                    } else {
                        arr.push(`${el} - new request`)
                        isUpdateRequired = true;
                        newReqs.push(el)
                    }
                })
            }
            oldOnes.set(path, notFoundReqs);
            newOnes.set(path, newReqs);
        } else {
            arr.push(...actualUrls.map(el => `${el} - new request`))
            newOnes.set(path, actualUrls);
            isUpdateRequired = true;
        }
        verifiedSequence.set(path, arr);
    });

    oldOnes.forEach((urls, path) => urls.map(url => console.log(`not found in actual: ${path} ${url}`)))
    newOnes.forEach((urls, path) => urls.map(url => console.log(`new request: ${path} ${url}`)))

    console.log(`----------------Finished---------------`)
    return { isUpdateRequired, verifiedSequence };
}