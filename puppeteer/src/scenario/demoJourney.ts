import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { dataTestSubj } from '../puppeteer/helpers'

export async function run(options: Config, page: puppeteer.Page) {
    await page.goto(options.baseUrl, {
        waitUntil: 'networkidle0',
    });

    //login
    const cloudLoginLocator = dataTestSubj('loginCard-basic/cloud-basic')
    if (await page.$(cloudLoginLocator) !== null) {
        await page.click(cloudLoginLocator);
    }

    await page.type(dataTestSubj('loginUsername'), options.username);
    await page.type(dataTestSubj('loginPassword'), options.password);
    await page.click(dataTestSubj('loginSubmit'));
    await page.waitForNavigation({ waitUntil: 'networkidle0' });

    // go to discover
    await page.goto(options.baseUrl + `/app/discover`
        , {
            waitUntil: 'networkidle0',
        });
    await page.waitForSelector(dataTestSubj('loadingSpinner'), { hidden: true });

    // console.log('2nd query')
    await selectDatePicker({ num: '5', unit: 'd' }, page)
    await waitForChartToLoad(page);

    // console.log('3rd query')
    await selectDatePicker({ num: '30', unit: 'd' }, page)
    await waitForChartToLoad(page);

    // load dashboard
    await page.goto(options.baseUrl + '/app/dashboards#/view/722b74f0-b882-11e8-a6d9-e546fe2bba5f', {
        waitUntil: 'networkidle0',
    });
    await page.waitForFunction(`document.querySelectorAll('[data-test-subj="dashboardPanel"]').length == 15`);
    await page.waitForTimeout(5000);

    // load canvas workpad
    await page.goto(options.baseUrl + '/app/canvas#/', {
        waitUntil: 'networkidle0',
    });
    await page.goto(options.baseUrl + '/app/canvas#/workpad/workpad-e08b9bdb-ec14-4339-94c4-063bddfd610e', {
        waitUntil: 'networkidle0',
    });
    await page.waitForTimeout(5000);
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
    await page.waitForTimeout(10000);
}