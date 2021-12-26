export const svgContentToUrl = (svgContent: string) => {
    const base64Data = btoa(svgContent)
    return "data:image/svg+xml;base64," + base64Data
}
