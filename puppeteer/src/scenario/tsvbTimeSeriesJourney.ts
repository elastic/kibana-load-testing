import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { dataTestSubj, loginIfNeeded, navigate } from '../puppeteer/helpers'
import { PendingRequests } from '../puppeteer/pendingRequests'

export async function run(options: Config, page: puppeteer.Page) {
    // loading Kibana
    await navigate(page, options.baseUrl);
    await loginIfNeeded(options, page);
    
    const pendingXHR = new PendingRequests(page);
    await page.goto(options.baseUrl + `/app/visualize#/edit/45e07720-b890-11e8-a6d9-e546fe2bba5f`
        , {
            waitUntil: 'networkidle0',
        });
    await page.waitForSelector(dataTestSubj('loadingSpinner'), { hidden: true });
    await pendingXHR.waitOnceForAllXhrFinished();
}