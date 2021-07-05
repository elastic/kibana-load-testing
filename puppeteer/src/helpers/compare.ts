import fs from 'fs'
import { url } from 'inspector';
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
                } else if (expectedUrls.indexOf(actualUrls[0]) > 0) {
                    arr.push(`${actualUrls[0]} - changed order`)
                    expectedUrls = expectedUrls.slice(expectedUrls.indexOf(actualUrls[0]), 1);
                    actualUrls.shift();
                } else if (actualUrls[0].includes('bsearch')) {
                    arr.push(`${actualUrls[0]} - extra call`)
                    actualUrls.shift();
                } else if (expectedUrls[0].includes('bsearch')) {
                    arr.push(`${actualUrls[0]} - redundant call`)
                    expectedUrls.shift();
                } else {
                    arr.push(`${actualUrls[0]} - new request`)
                    newReqs.push(actualUrls[0])
                    isNewRequestFound = true;
                    actualUrls.shift();
                }
            }
            if (expectedUrls.length > 0) {
                console.log(`Some calls are no longer executed`)
                expectedUrls.map(el => {
                    actualUrls.push(`${el} - not found`)
                    notFoundReqs.push(el)
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