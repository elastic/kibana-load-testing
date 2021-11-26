#!/usr/bin/env node

import { scenarioRunner } from './runner';
import yargs = require("yargs");
import { isWebUri } from 'valid-url'
import chalk from 'chalk'
import fs from 'fs'
import { resolve } from 'path';
import { Config } from './types/config'

process.on('unhandledRejection', error => {
    console.log(error);
});

(async function run() {
    try {
        let runConfig: Config;
        const argv = await yargs
            .version(false)
            .usage("Usage: --baseUrl http://localhost:5620 --username elastic --password changeme --version 8.0.0")
            .option("config", { alias: "config", describe: "Config file path", type: "string", demandOption: false })
            .option("simulation", { alias: "simulation", describe: "Simulation name", type: "string", demandOption: true })
            .option("baseUrl", { alias: "baseUrl", describe: "Kibana base url", type: "string", demandOption: false })
            .option("username", { alias: "username", describe: "Kibana username", type: "string", demandOption: false })
            .option("password", { alias: "password", describe: "Kibana password", type: "string", demandOption: false })
            .option("version", { alias: "version", describe: "Kibana version", type: "string", demandOption: false })
            .option("scenarioCheck", { alias: "scenarioCheck", describe: "Check scenario up-to-date", type: "boolean", demandOption: false })
            .option("headless", { alias: "headless", describe: "Run browser headless", type: "boolean", demandOption: false })
            .argv;
            const { config, simulation, baseUrl, username, password, version, scenarioCheck, headless } = argv
            if (config && fs.existsSync(resolve(config))) {
                runConfig = JSON.parse(fs.readFileSync(resolve(config), 'utf8'));
                if (baseUrl) {
                    console.log(`Overriding 'baseUrl' arg with '${baseUrl}'`)
                    runConfig.baseUrl = baseUrl
                }
                if (username) {
                    console.log(`Overriding 'username' arg with '${username}'`)
                    runConfig.username = username
                }
                if (password) {
                    console.log(`Overriding 'password' arg with '${password}'`)
                    runConfig.password = password
                }
                if (version) {
                    console.log(`Overriding 'version' arg with '${version}'`)
                    runConfig.version = version
                }
                if (scenarioCheck) {
                    console.log(`Overriding 'scenarioCheck' arg with '${scenarioCheck}'`)
                    runConfig.scenarioCheck = scenarioCheck
                }
                if (headless) {
                    console.log(`Overriding 'version' arg with '${headless}'`)
                    runConfig.headless = headless
                }
            } else {
                if (baseUrl && username && password && version) {
                    runConfig = { baseUrl, username, password, version, scenarioCheck, headless }
                } else throw new Error("missing arguments")
            }

        if (!isWebUri(runConfig.baseUrl)) {
            throw new Error(chalk.red(`Invalid 'baseUrl'=${runConfig.baseUrl}, valid example: 'http://localhost:5620'`));
        }
        if (!(/(7|8).\d{1,2}.\d/.test(runConfig.version))) {
            throw new Error(chalk.red(`Invalid 'version'=${runConfig.version}, valid example: '7.12.1'`));
        }

        const simulations = JSON.parse(fs.readFileSync(resolve('./simulations.json'), 'utf8'));
        const scenario = simulations[simulation]
        if (scenario) {
            await scenarioRunner(runConfig, [scenario])
        } else {
            console.log(`no scenario found for '${simulation}' simulation`)
        }
    } catch (err) {
        console.log(err);
        process.exit(1);
    }

})()
