export function isNotResource(url: string) {
    return !(/(js|css|woff2|jpeg|png|svg|json|ttf)$/.test(url))
}

export function mapToJSON(map: Map<string, any>) {
    let jsonObject: any = {};
    map.forEach((value, key) => {
        jsonObject[key] = value
    });
    return <JSON>jsonObject
}