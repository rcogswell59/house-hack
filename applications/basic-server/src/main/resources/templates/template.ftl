<#macro page title="HouseHack">
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name=viewport content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="/static/styles/reset.css">
        <link rel="stylesheet" href="/static/styles/style.css">
        <link rel="stylesheet" href="/static/styles/app.css">
        <link rel="icon" type="image/svg" href="/static/images/favicon.svg">
        <title>${title} · HouseHack</title>
    </head>
    <body>
    <header>
        <div class="container">
            <h1><a href="/report">HouseHack</a></h1>
            <nav>
                <a href="/report">Report</a>
                <a href="/profile">Profile</a>
                <a href="/api/listings">API</a>
                <a href="/health">Health</a>
            </nav>
        </div>
    </header>
    <section class="callout">
        <div class="container">
            What you'd <span class="branded">actually pay each month</span> for a 2–4 unit property near work, after rent and every program you qualify for.
        </div>
    </section>
    <main>
        <#nested>
    </main>
    <footer>
        <div class="container">
            HouseHack · Brevard County, FL · Big Data course project, Fall 2026. Estimates only; not lending or tax advice.
        </div>
    </footer>
    </body>
    </html>
</#macro>
