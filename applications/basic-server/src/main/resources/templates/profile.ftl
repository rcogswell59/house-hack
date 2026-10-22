<#import "template.ftl" as layout />
<#macro field name label hint="" step="any">
    <label>
        <span>${label}</span>
        <input type="number" step="${step}" name="${name}" value="${form[name]!}" required>
        <#if hint?has_content><small>${hint}</small></#if>
    </label>
</#macro>
<#macro check name label>
    <label class="check"><input type="checkbox" name="${name}" <#if (form[name]!"") == "on">checked</#if>> ${label}</label>
</#macro>

<@layout.page title="Buyer profile">
    <section>
        <div class="container">
            <h2>Buyer profile</h2>
            <p class="muted">Every number on the report is computed from this profile. Change it and the analyzer re-scores all listings within a minute.</p>

            <#if errors?has_content>
                <div class="errors">
                    <strong>Please fix the following:</strong>
                    <ul><#list errors as e><li>${e}</li></#list></ul>
                </div>
            </#if>

            <form method="post" action="/profile" class="profile-form">
                <fieldset>
                    <legend>About you</legend>
                    <label><span>Name</span><input type="text" name="name" value="${form.name!}" required></label>
                    <@field name="annualIncome" label="Annual gross income ($)" hint="Base plus any bonus/overtime with a 2-year history." step="1"/>
                    <@field name="liquidSavings" label="Liquid savings ($)" step="1"/>
                    <@field name="creditScore" label="Credit score" step="1"/>
                    <@field name="householdSize" label="Household size" step="1"/>
                    <@field name="otherMonthlyDebts" label="Other monthly debt payments ($)" hint="Car, student loans, cards." step="1"/>
                    <@check name="firstTimeBuyer" label="First-time buyer (no ownership in the last 3 years)"/>
                    <@check name="hthEligibleOccupation" label="Hometown Heroes occupation (healthcare, school staff, first responder, public safety, child care) at a Florida employer"/>
                    <@check name="veteran" label="Veteran or active-duty service member"/>
                </fieldset>

                <fieldset>
                    <legend>Where and what</legend>
                    <@field name="workplaceLat" label="Workplace latitude"/>
                    <@field name="workplaceLng" label="Workplace longitude"/>
                    <@field name="maxDriveMinutes" label="Max drive to work (minutes)" step="1"/>
                    <@field name="unitsMin" label="Minimum units" step="1"/>
                    <@field name="unitsMax" label="Maximum units" hint="FHA owner-occupied financing covers 2–4 units." step="1"/>
                </fieldset>

                <fieldset>
                    <legend>Assumptions (fractions, e.g. 0.08 = 8%)</legend>
                    <@field name="mortgageRate" label="Mortgage rate"/>
                    <@field name="insuranceRate" label="Insurance (per year, of price)"/>
                    <@field name="closingCostRate" label="Closing costs (of price)"/>
                    <@field name="vacancyRate" label="Vacancy reserve (of rent)"/>
                    <@field name="maintenanceRate" label="Maintenance reserve (of rent)"/>
                    <@field name="roadFactor" label="Road factor (straight-line × factor)"/>
                    <@field name="avgSpeedMph" label="Average speed (mph)"/>
                </fieldset>

                <button type="submit"><#if saved>Save changes<#else>Create profile</#if></button>
            </form>
        </div>
    </section>
</@layout.page>
