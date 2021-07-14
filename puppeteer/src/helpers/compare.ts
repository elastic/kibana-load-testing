import fs from 'fs'
import { resolve } from 'path';

export function compareWithBaseline(scenario: string, actualSequence: Map<string, string[]>) {
    console.log(`${scenario} scenario: comparing recorded requestes with baseline:`)
    let isNewRequestFound = false;
    let baselinePath = resolve(__dirname, '..', '..', 'baseline', scenario, 'requests.json');
    const expectedSequence = new Map(Object.entries(JSON.parse(fs.readFileSync(baselinePath, 'utf8')) as JSON)) as Map<string, string[]>;

    const verifiedSequence = new Map<string, string[]>();

    const newOnes = new Map<string, string[]>();
    const oldOnes = new Map<string, string[]>();

    actualSequence.forEach((actualUrls, path) => {
        const actualConst = actualUrls;
        let arr = Array<string>();
        let newReqs = Array<string>();
        let notFoundReqs = Array<string>();
        if (expectedSequence.has(path)) {
            let expectedUrls = expectedSequence.get(path) || []
            while ((expectedUrls.length > 0) && (actualUrls.length > 0)) {
                if (actualUrls[0] === expectedUrls[0]) {
                    arr.push(`${actualUrls[0]} - ok`)
                    actualUrls.shift();
                    expectedUrls.shift();
                } else {
                    // new request
                    if (expectedUrls.indexOf(actualUrls[0]) == -1) {
                        arr.push(`${actualUrls[0]} - new request`);
                        newReqs.push(actualUrls[0]);
                        isNewRequestFound = true;
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
                    if (el.includes('bsearch') && actualConst.indexOf('/internal/bsearch')) {
                        console.log(`extra '${path} /internal/bsearch' is not found, but was in baseline`)
                    } else {
                        actualUrls.push(`${el} - not found`)
                        notFoundReqs.push(el)
                    }
                })
            }
            if (actualUrls.length > 0) {
                actualUrls.map(el => {
                    if (el.includes('bsearch') && actualConst.indexOf('/internal/bsearch')) {
                        console.log(`extra '${path} /internal/bsearch' is found, but was not baseline`)
                    } else {
                        actualUrls.push(`${el} - new request`)
                        notFoundReqs.push(el)
                    }
                })
            }
            oldOnes.set(path, notFoundReqs);
            newOnes.set(path, newReqs);
        } else {
            arr.push(...actualUrls.map(el => `${el} - new request`))
            newOnes.set(path, actualUrls);
        }
        verifiedSequence.set(path, arr);
    });

    oldOnes.forEach((urls, path) => urls.map(url => console.log(`not found in actual: ${path} ${url}`)))
    newOnes.forEach((urls, path) => urls.map(url => console.log(`new request: ${path} ${url}`)))

    console.log(`----------------Finished---------------`)
    return { isNewRequestFound, verifiedSequence };
}