import fs from 'fs'
import { resolve } from 'path';

export function compareWithBaseline(scenario: string, actualSequence: Map<string, string[]>) {
    let baselinePath = resolve(__dirname, '..', '..', 'baseline', scenario, 'requests.json');
    const expectedSequence = new Map(Object.entries(JSON.parse(fs.readFileSync(baselinePath, 'utf8')) as JSON)) as Map<string, string[]>;

    const verifiedSequence = new Map<string, string[]>();

    actualSequence.forEach((actualUrls, path) => {
        let arr = Array<string>();
        if (expectedSequence.has(path)) {
            const expectedUrls = expectedSequence.get(path) || []
            while ((expectedUrls.length > 0) && (actualUrls.length > 0)) {
                if (actualUrls[0] === expectedUrls[0]) {
                    arr.push(`${actualUrls[0]} - ok`)
                    actualUrls.shift();
                    expectedUrls.shift();
                } else if (expectedUrls.indexOf(actualUrls[0]) > 0) {
                    arr.push(`${actualUrls[0]} - changed order`)
                    expectedUrls.slice(expectedUrls.indexOf(actualUrls[0]), 1);
                    actualUrls.shift();
                } else if (actualUrls[0].includes('bsearch')) {
                    arr.push(`${actualUrls[0]} - extra call`)
                    actualUrls.shift();
                } else {
                    arr.push(`${actualUrls[0]} - new request`)
                    actualUrls.shift();
                }
            }
            if (expectedUrls.length > 0) {
                expectedUrls.map(el => actualUrls.push(`${el} - not found`))
            }
        } else {
            arr.push(...actualUrls.map(el => `${el} - new request`))
        }
        verifiedSequence.set(path, arr);
    });

    return verifiedSequence;
}