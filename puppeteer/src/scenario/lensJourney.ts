import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { dataTestSubj, navigate, loginIfNeeded } from '../puppeteer/helpers'
import { PendingRequests } from '../puppeteer/pendingRequests'

export async function run(options: Config, page: puppeteer.Page) {
    // loading Kibana
    await navigate(page, options.baseUrl);
    await loginIfNeeded(options, page);
    
    const pendingXHR = new PendingRequests(page);
    await page.goto(options.baseUrl + `/app/lens#/edit/c762b7a0-f5ea-11eb-a78e-83aac3c38a60?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-15m,to:now))`
        , {
            waitUntil: 'networkidle0',
        });
    await page.waitForSelector(dataTestSubj('loadingSpinner'), { hidden: true });
    await pendingXHR.waitOnceForAllXhrFinished();
}