import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { dataTestSubj, navigate, loginIfNeeded } from '../puppeteer/helpers'
import { PendingRequests } from '../puppeteer/pendingRequests'

export async function run(options: Config, page: puppeteer.Page) {
    // loading Kibana
    await navigate(page, options.baseUrl);
    await loginIfNeeded(options, page);
    // go to discover
    let pendingXHR = new PendingRequests(page);
    await page.goto(options.baseUrl + `/app/discover`
        , {
            waitUntil: 'networkidle0',
        });
    await page.waitForSelector(dataTestSubj('loadingSpinner'), { hidden: true });
    await pendingXHR.waitOnceForAllXhrFinished();
    // console.log('2nd query')
    pendingXHR = new PendingRequests(page);
    await selectDatePicker({ num: '5', unit: 'd' }, page)
    await waitForChartToLoad(page);
    await pendingXHR.waitOnceForAllXhrFinished();
    // console.log('3rd query')
    pendingXHR = new PendingRequests(page);
    await selectDatePicker({ num: '30', unit: 'd' }, page)
    await waitForChartToLoad(page);
    await pendingXHR.waitOnceForAllXhrFinished();

    // load dashboard
    pendingXHR = new PendingRequests(page);
    await page.goto(options.baseUrl + '/app/dashboards#/view/722b74f0-b882-11e8-a6d9-e546fe2bba5f', {
        waitUntil: 'networkidle0',
    });
    await page.waitForFunction(`document.querySelectorAll('[data-test-subj="dashboardPanel"]').length == 15`);
    await pendingXHR.waitOnceForAllXhrFinished();

    // load canvas workpad
    await navigate(page, options.baseUrl + '/app/canvas#/');
    pendingXHR = new PendingRequests(page);
    await page.goto(options.baseUrl + '/app/canvas#/workpad/workpad-e08b9bdb-ec14-4339-94c4-063bddfd610e', {
        waitUntil: 'networkidle0',
    });
    await page.waitForFunction(`document.querySelectorAll('[data-test-subj="canvasWorkpadPage"] [data-test-subj="canvasWorkpadPageElementContent"]').length == 25`);
    await pendingXHR.waitOnceForAllXhrFinished();
}

async function selectDatePicker(value: { num: string, unit: string }, page: puppeteer.Page) {
    await page.waitForFunction(`document.querySelector('[data-test-subj="superDatePickerToggleQuickMenuButton"]') && document.querySelector('[data-test-subj="superDatePickerToggleQuickMenuButton"]').clientHeight != 0`);
    await page.click(dataTestSubj('superDatePickerShowDatesButton'));
    await page.waitForFunction(`document.querySelector('[data-test-subj="superDatePickerstartDatePopoverButton"]') && document.querySelector('[data-test-subj="superDatePickerstartDatePopoverButton"]').clientHeight != 0`);
    await page.click(dataTestSubj('superDatePickerstartDatePopoverButton'));
    const input = await page.$(`[data-test-subj="superDatePickerRelativeDateInputNumber"]`);
    await input?.click({ clickCount: 3 });
    await page.keyboard.press('Backspace');
    await page.type(dataTestSubj('superDatePickerRelativeDateInputNumber'), value.num);
    await page.select(dataTestSubj('superDatePickerRelativeDateInputUnitSelector'), value.unit);
    await page.click(dataTestSubj('superDatePickerstartDatePopoverButton'));

    if (await page.$(dataTestSubj('superDatePickerApplyTimeButton')) !== null) {
        await page.click(dataTestSubj('superDatePickerApplyTimeButton'));
    } else {
        // Timepicker is embedded in query bar
        // click query bar submit button to apply time range
        await page.click(dataTestSubj('querySubmitButton'));
    }
}

async function waitForChartToLoad(page: puppeteer.Page) {
    if (await page.$(dataTestSubj('globalLoadingIndicator')) !== null) {
        await page.waitForSelector(dataTestSubj('globalLoadingIndicator'), { visible: false });
    }
    await page.waitForSelector(dataTestSubj('loadingSpinner'), { hidden: true });
    await page.waitForFunction("document.querySelectorAll('[data-ech-render-complete=true]').length == 1");
}