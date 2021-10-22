import puppeteer from 'puppeteer';
import { PendingRequests } from '../puppeteer/pendingRequests';
import { Config } from '../types/config'

export function dataTestSubj(value: string) {
    return `[data-test-subj="${value}"]`
}

export async function loginIfNeeded(options: Config, page: puppeteer.Page) {
    let pendingXHR = new PendingRequests(page);
    // check if Elastic cloud
    const cloudLoginLocator = dataTestSubj('loginCard-basic/cloud-basic')
    if (await page.$(cloudLoginLocator) !== null) {
        await page.click(cloudLoginLocator);
    }
    // login if needed
    if (await page.$(dataTestSubj('loginUsername')) !== null) {
        await page.type(dataTestSubj('loginUsername'), options.username);
        await page.type(dataTestSubj('loginPassword'), options.password);
        await page.click(dataTestSubj('loginSubmit'));
    }
    await page.waitForNavigation({ waitUntil: 'networkidle0' });
    await pendingXHR.waitOnceForAllXhrFinished();
}

export async function navigate(
    page: puppeteer.Page,
    url: string,
    waitConditions: puppeteer.PuppeteerLifeCycleEvent[] | undefined = ['networkidle0']) {
    const pendingXHR = new PendingRequests(page);
    await page.goto(url, {
        waitUntil: waitConditions,
    });
    await pendingXHR.waitOnceForAllXhrFinished();
}

export async function waitForNetwork0(page: puppeteer.Page, timeout = 200) {
    await new Promise((resolve) => {
        let timer: NodeJS.Timeout;
        page.on('response', () => {
            clearTimeout(timer);
            timer = setTimeout(resolve, timeout);
        });
    });
};