Drop two .ttf files in this folder, named EXACTLY:

    SpaceGrotesk-Bold.ttf     <- titles, metrics, numbers
    Poppins-Regular.ttf       <- body text, notes

Both are SIL Open Font License 1.1, so bundling them in the jar and
redistributing the app is allowed. Download once from:

    https://fonts.google.com/specimen/Space+Grotesk
    https://fonts.google.com/specimen/Poppins

They are loaded by UiTheme.loadFont() via getResourceAsStream("/fonts/...")
and registered with GraphicsEnvironment.registerFont(). Nothing is fetched at
runtime - once these files are here, Maven packages them into the jar and the
app stays fully offline.

If a file is missing or corrupt the app does NOT fail: UiTheme logs one line
to automacropro.log and falls back to SansSerif. So the UI works right now,
before you add them - it just uses the fallback face. Run
ThemeSelfCheck to see which font actually resolved.
