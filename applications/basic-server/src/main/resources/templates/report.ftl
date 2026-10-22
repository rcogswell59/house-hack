<#import "template.ftl" as layout />

<@layout.page title="Ranked report">
    <section>
        <div class="container">
            <#if !hasProfile>
                <h2>Welcome</h2>
                <p>Start by <a href="/profile">creating your buyer profile</a>. The collector pulls listings daily and the analyzer ranks them against your numbers.</p>
            <#else>
                <h2>Ranked by net monthly cost <span class="muted">for ${profileName}</span></h2>
                <p class="muted">
                    ${rows?size} analyzed · ${activeCount} active listings ·
                    <#if pendingCount == 1>1 pending<#else>${pendingCount} pending</#if> (waiting on rent estimate, parcel data, or outside your filters).
                </p>

                <#if rows?has_content>
                    <table class="report">
                        <thead>
                        <tr>
                            <th>#</th><th>Address</th><th>City</th><th>Price</th><th>Units</th>
                            <th>Net monthly cost</th><th>Cash to close</th><th>Lender</th><th>Drive</th>
                        </tr>
                        </thead>
                        <tbody>
                        <#list rows as row>
                            <tr>
                                <td>${row.rank}</td>
                                <td><a href="/listings/${row.listingId}">${row.address}</a></td>
                                <td>${row.city}</td>
                                <td>${row.price}</td>
                                <td>${row.units}</td>
                                <td class="num strong">${row.netMonthlyCost}</td>
                                <td class="num">${row.cashToClose}</td>
                                <td><#if row.lenderPass><span class="badge pass">pass</span><#else><span class="badge fail">fail</span></#if></td>
                                <td>~${row.driveMinutes} min</td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                    <p class="muted small">Net monthly cost = mortgage + MIP + taxes + insurance + reserves − 100% of other units' rent. "Lender" is the FHA view (75% rent rule, self-sufficiency for 3–4 units), shown separately on purpose. Drive times are straight-line estimates.</p>
                <#else>
                    <p>No analyzed listings yet. The analyzer runs every minute once listings and rent estimates arrive.</p>
                </#if>
            </#if>
        </div>
    </section>
</@layout.page>
