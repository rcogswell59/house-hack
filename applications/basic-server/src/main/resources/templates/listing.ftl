<#import "template.ftl" as layout />

<@layout.page title="${listing.address}">
    <section>
        <div class="container">
            <p><a href="/report">← Back to report</a></p>
            <h2>${listing.address}<#if listing.city??>, ${listing.city}</#if> <#if listing.zip??>${listing.zip}</#if></h2>
            <p class="muted">Listed at <strong>${price}</strong> · ${listing.propertyType!"—"} ·
                ${(listing.bedrooms?c)!"?"} bd / ${(listing.bathrooms?c)!"?"} ba · ${(listing.sqft?c)!"?"} sqft · built ${(listing.yearBuilt?c)!"?"}</p>

            <div class="grid">
                <div class="card">
                    <h3>County parcel</h3>
                    <#if parcel??>
                        <dl>
                            <dt>Parcel</dt><dd>${parcel.parcelId}</dd>
                            <dt>Residential units</dt><dd>${(parcel.units?c)!"unknown"}</dd>
                            <dt>Just value</dt><dd>${parcelJustValue}</dd>
                            <dt>Land use code</dt><dd>${parcel.landUseCode!"—"}</dd>
                            <dt>Year built</dt><dd>${(parcel.yearBuilt?c)!"—"}</dd>
                        </dl>
                    <#else>
                        <p class="muted">No parcel matched yet. Unit count falls back to bedrooms ÷ 2.</p>
                    </#if>
                </div>
                <div class="card">
                    <h3>Rent estimate</h3>
                    <#if rent??>
                        <dl>
                            <dt>Per unit</dt><dd>${rentPerUnit}</dd>
                            <dt>Assumed beds / unit</dt><dd>${(rent.bedsAssumed?c)!"—"}</dd>
                        </dl>
                    <#else>
                        <p class="muted">Waiting on a rent estimate; this listing can't be ranked until one arrives.</p>
                    </#if>
                </div>
            </div>

            <#if detail??>
                <h3>Buyer view <span class="muted">— what you would pay</span></h3>
                <table class="kv">
                    <#list detail.buyer as line><tr><th>${line.first}</th><td class="num">${line.second}</td></tr></#list>
                </table>
                <p class="muted small">Assumes ${detail.units} units (${detail.unitsSource}), millage ${detail.millage} (${detail.millageSource}), homestead exemption on your unit only, FHA 3.5% down. Drive ≈ ${detail.driveMinutes} min.</p>

                <h3>Lender view <span class="muted">— will FHA approve it</span>
                    <#if detail.lenderPass><span class="badge pass">pass</span><#else><span class="badge fail">fail</span></#if></h3>
                <table class="kv">
                    <#list detail.lender as line><tr><th>${line.first}</th><td class="num">${line.second}</td></tr></#list>
                </table>

                <h3>Programs <span class="muted">— total assistance applied: ${detail.totalAssistance}</span></h3>
                <ul class="programs">
                    <#list detail.programs as p>
                        <li>
                            <strong>${p.program}</strong>
                            <#if p.eligible><span class="badge pass">eligible</span> ${p.assistance}<#else><span class="badge fail">not eligible</span></#if>
                            <ul><#list p.reasons as r><li class="small">${r}</li></#list></ul>
                        </li>
                    </#list>
                </ul>
                <p class="muted small">Analyzed ${detail.runAt} UTC.</p>
            <#else>
                <p>Not analyzed yet for the current profile.</p>
            </#if>
        </div>
    </section>
</@layout.page>
