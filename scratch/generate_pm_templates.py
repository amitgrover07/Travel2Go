import json

# Read the excel dump data
with open('scratch/excel_data.json', 'r', encoding='utf-8') as f:
    excel_data = json.load(f)

# Define our framework configuration templates
templates_js = """// PM Framework Templates with default data and live calculators
// Generated from PM Framework Calculators.xlsx

export const PM_TEMPLATES = {
  RICE: {
    title: "RICE Scoring",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Comparing a backlog of features/ideas objectively. Reach = how many users this affects per period. Impact = per-user effect (3=massive, 2=high, 1=medium, 0.5=low, 0.25=minimal). Confidence = how sure you are (100/80/50%). Effort = person-months of work.",
    inPlainWords: "Score = (Reach × Impact × Confidence) ÷ Effort. The higher the score, the sooner you build it.",
    howToRun: [
      "Estimate Reach (users/qtr) from real analytics, not hope.",
      "Rate Impact on a fixed scale (3=massive, 2=high, 1=medium, 0.5=low, 0.25=minimal).",
      "Set Confidence honestly as a percentage (100%, 80%, 50%).",
      "Estimate Effort in person-months.",
      "Compute RICE Score, sort descending, and rank."
    ],
    watchOutFor: [
      "Inflating Reach with wishful numbers.",
      "Ignoring the Confidence column, which is there to punish hand-waving.",
      "Treating the score as gospel; it's an input to judgement, not a verdict."
    ],
    headers: [
      { key: "name", label: "Feature / Idea", type: "text", editable: true },
      { key: "reach", label: "Reach (users/qtr)", type: "number", editable: true },
      { key: "impact", label: "Impact (0.25-3)", type: "number", editable: true },
      { key: "confidence", label: "Confidence (%)", type: "percent", editable: true },
      { key: "effort", label: "Effort (p-months)", type: "number", editable: true },
      { key: "score", label: "RICE Score", type: "number", editable: false },
      { key: "rank", label: "Rank", type: "number", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "1-tap reorder", reach: 42000, impact: 2.0, confidence: 0.9, effort: 2.0 },
      { id: "2", name: "UPI autopay", reach: 30000, impact: 3.0, confidence: 0.8, effort: 3.0 },
      { id: "3", name: "Hindi voice search", reach: 18000, impact: 1.0, confidence: 0.5, effort: 4.0 },
      { id: "4", name: "Loyalty coins", reach: 55000, impact: 1.0, confidence: 0.8, effort: 3.0 },
      { id: "5", name: "Group ordering", reach: 22000, impact: 2.0, confidence: 0.7, effort: 5.0 },
      { id: "6", name: "Live order tracking", reach: 60000, impact: 2.0, confidence: 0.9, effort: 2.0 },
      { id: "7", name: "Referral rewards", reach: 48000, impact: 1.0, confidence: 0.8, effort: 1.5 },
      { id: "8", name: "Dark mode", reach: 35000, impact: 0.5, confidence: 1.0, effort: 1.0 }
    ],
    calculate: (rows) => {
      const calculated = rows.map(row => {
        const reach = Number(row.reach) || 0;
        const impact = Number(row.impact) || 0;
        const confidence = Number(row.confidence) || 0;
        const effort = Number(row.effort) || 1; // Avoid division by zero
        const score = Math.round((reach * impact * confidence) / effort);
        return { ...row, score };
      });
      // Sort and rank
      const sorted = [...calculated].sort((a, b) => b.score - a.score);
      return calculated.map(row => {
        const rank = sorted.findIndex(r => r.id === row.id) + 1;
        return { ...row, rank };
      });
    }
  },

  WeightedScoring: {
    title: "Weighted Scoring Model",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Choosing between a few strategic options (e.g. which market to enter) where several criteria matter unequally. Set weights on the weights row (they must sum to 100%). Score each option 1-5 on each criterion.",
    inPlainWords: "Rank options against weighted criteria — weights carry the strategy and must sum to 100%.",
    howToRun: [
      "List the criteria that actually drive the decision.",
      "Agree on weights with stakeholders BEFORE scoring anything.",
      "Score each option 1-5 on each criterion.",
      "Weighted total = SUM(score × weight); rank."
    ],
    watchOutFor: [
      "Setting weights after seeing scores to justify a favourite.",
      "Too many criteria, so everything averages to the middle.",
      "Pseudo-precision: 1-5 is opinion, treat it as such."
    ],
    weights: {
      fit: 0.35,
      revenue: 0.30,
      speed: 0.20,
      risk: 0.15
    },
    headers: [
      { key: "name", label: "Option", type: "text", editable: true },
      { key: "fit", label: "Strategic fit (1-5)", type: "number", editable: true },
      { key: "revenue", label: "Revenue upside (1-5)", type: "number", editable: true },
      { key: "speed", label: "Speed to ship (1-5)", type: "number", editable: true },
      { key: "risk", label: "Low risk (1-5)", type: "number", editable: true },
      { key: "score", label: "Weighted total", type: "number", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Tier-2 metros", fit: 4, revenue: 5, speed: 3, risk: 3 },
      { id: "2", name: "Tier-3 towns", fit: 5, revenue: 3, speed: 2, risk: 4 },
      { id: "3", name: "Premium urban", fit: 3, revenue: 4, speed: 4, risk: 3 },
      { id: "4", name: "SMB / kirana", fit: 4, revenue: 4, speed: 5, risk: 4 }
    ],
    calculate: (rows, weights) => {
      const fitW = Number(weights.fit) || 0;
      const revW = Number(weights.revenue) || 0;
      const speedW = Number(weights.speed) || 0;
      const riskW = Number(weights.risk) || 0;
      return rows.map(row => {
        const fit = Number(row.fit) || 0;
        const revenue = Number(row.revenue) || 0;
        const speed = Number(row.speed) || 0;
        const risk = Number(row.risk) || 0;
        const score = Number((fit * fitW + revenue * revW + speed * speedW + risk * riskW).toFixed(2));
        return { ...row, score };
      });
    }
  },

  ValueVsEffort: {
    title: "Value vs Effort",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "A fast, visual triage in a workshop or meeting when you need a decision in ten minutes, not a spreadsheet. Rate Value and Effort 1-10.",
    inPlainWords: "The fastest triage: do the high-value, low-effort Quick Wins first; avoid the Money Pits.",
    howToRun: [
      "Rate every item on Value and Effort (1-10).",
      "Plot on the 2x2 grid.",
      "Sequence Quick Wins first to earn credibility.",
      "Justify any Big Bet with a strategic reason; timebox it.",
      "Say no to Money Pits out loud so they stop coming back."
    ],
    watchOutFor: [
      "Everyone rating their own idea 'high value'.",
      "Underestimating effort (the planning fallacy is universal).",
      "Living only in Quick Wins and never making a strategic Big Bet."
    ],
    headers: [
      { key: "name", label: "Item / Feature", type: "text", editable: true },
      { key: "value", label: "Value (1-10)", type: "number", editable: true },
      { key: "effort", label: "Effort (1-10)", type: "number", editable: true },
      { key: "quadrant", label: "Quadrant", type: "text", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Live tracking", value: 9, effort: 3 },
      { id: "2", name: "UPI autopay", value: 8, effort: 6 },
      { id: "3", name: "Voice search", value: 4, effort: 7 },
      { id: "4", name: "Dark mode", value: 3, effort: 2 },
      { id: "5", name: "Group ordering", value: 6, effort: 8 },
      { id: "6", name: "Referral rewards", value: 7, effort: 3 },
      { id: "7", name: "Loyalty coins", value: 6, effort: 4 },
      { id: "8", name: "AI menu recos", value: 8, effort: 9 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const value = Number(row.value) || 0;
        const effort = Number(row.effort) || 0;
        let quadrant = "";
        if (value >= 5 && effort < 5) quadrant = "Quick Win";
        else if (value >= 5 && effort >= 5) quadrant = "Big Bet";
        else if (value < 5 && effort < 5) quadrant = "Fill-in";
        else quadrant = "Money Pit";
        return { ...row, quadrant };
      });
    }
  },

  KanoModel: {
    title: "Kano Model",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Balancing a roadmap so it isn't all shiny delighters with a broken core - or all boring table-stakes. Survey users with functional and dysfunctional questions.",
    inPlainWords: "Features fall into Must-bes, Performance and Delighters - and yesterday's delighter becomes tomorrow's must-be.",
    howToRun: [
      "Run the paired Kano survey (feature functional vs dysfunctional) on real users.",
      "Tally responses and classify each user's response into Attractive, Performance, Must-be, Indifferent, or Reverse.",
      "Better coefficient = (Attractive + Performance) / Total.",
      "Worse coefficient = -(Performance + Must-be) / Total.",
      "Classification = category with the highest vote count."
    ],
    watchOutFor: [
      "Chasing delighters while a must-be is broken.",
      "Assuming a delighter stays delightful forever (decays over time).",
      "Skipping the survey and just guessing the categories."
    ],
    headers: [
      { key: "name", label: "Feature / Idea", type: "text", editable: true },
      { key: "attractive", label: "Attractive (Delight)", type: "number", editable: true },
      { key: "performance", label: "Performance", type: "number", editable: true },
      { key: "mustBe", label: "Must-be (Basic)", type: "number", editable: true },
      { key: "indifferent", label: "Indifferent", type: "number", editable: true },
      { key: "reverse", label: "Reverse", type: "number", editable: true },
      { key: "better", label: "Better coeff. (0 to 1)", type: "number", editable: false },
      { key: "worse", label: "Worse coeff. (-1 to 0)", type: "number", editable: false },
      { key: "classification", label: "Kano Category", type: "text", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Live tracking", attractive: 30, performance: 45, mustBe: 20, indifferent: 5, reverse: 0 },
      { id: "2", name: "UPI autopay", attractive: 15, performance: 40, mustBe: 40, indifferent: 5, reverse: 0 },
      { id: "3", name: "Hindi voice", attractive: 50, performance: 20, mustBe: 5, indifferent: 25, reverse: 0 },
      { id: "4", name: "Loyalty coins", attractive: 55, performance: 25, mustBe: 5, indifferent: 15, reverse: 0 },
      { id: "5", name: "Dark mode", attractive: 20, performance: 10, mustBe: 5, indifferent: 60, reverse: 5 },
      { id: "6", name: "Fast delivery", attractive: 5, performance: 35, mustBe: 55, indifferent: 5, reverse: 0 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const attractive = Number(row.attractive) || 0;
        const performance = Number(row.performance) || 0;
        const mustBe = Number(row.mustBe) || 0;
        const indifferent = Number(row.indifferent) || 0;
        const reverse = Number(row.reverse) || 0;
        const total = attractive + performance + mustBe + indifferent + reverse;
        
        const better = total > 0 ? (attractive + performance) / total : 0;
        const worse = total > 0 ? -(performance + mustBe) / total : 0;
        
        // Find highest vote count
        const votes = {
          "Delighter": attractive,
          "Performance": performance,
          "Must-be": mustBe,
          "Indifferent": indifferent,
          "Reverse": reverse
        };
        let classification = "Indifferent";
        let maxVotes = -1;
        for (const [cat, v] of Object.entries(votes)) {
          if (v > maxVotes) {
            maxVotes = v;
            classification = cat;
          }
        }
        
        return {
          ...row,
          better: Number(better.toFixed(2)),
          worse: Number(worse.toFixed(2)),
          classification
        };
      });
    }
  },

  WSJF: {
    title: "WSJF Scoring",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Agile/SAFe backlogs where sequencing many items well matters more than a one-off pick. Ranks work by economic urgency per unit of effort.",
    inPlainWords: "Weighted Shortest Job First: do the shortest, most valuable jobs first. WSJF = Cost of Delay ÷ Job Size.",
    howToRun: [
      "Score each component relatively 1-10: User/Biz Value, Time Criticality, Risk/Opportunity.",
      "Cost of Delay = Value + Time Criticality + Risk/Opportunity.",
      "Estimate Job Size (effort/story points) relatively.",
      "WSJF = Cost of Delay ÷ Job Size; sort descending."
    ],
    watchOutFor: [
      "Everything marked 'urgent' (10), flattening Time Criticality.",
      "Using absolute instead of relative scores.",
      "Gaming Job Size down to jump the queue."
    ],
    headers: [
      { key: "name", label: "Feature / Job", type: "text", editable: true },
      { key: "value", label: "User/Biz Value (1-10)", type: "number", editable: true },
      { key: "criticality", label: "Time Criticality (1-10)", type: "number", editable: true },
      { key: "opportunity", label: "Risk/Opp Enable (1-10)", type: "number", editable: true },
      { key: "cod", label: "Cost of Delay", type: "number", editable: false },
      { key: "size", label: "Job Size (relative)", type: "number", editable: true },
      { key: "wsjf", label: "WSJF Score", type: "number", editable: false },
      { key: "rank", label: "Rank", type: "number", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Live tracking", value: 8, criticality: 7, opportunity: 5, size: 3 },
      { id: "2", name: "UPI autopay", value: 9, criticality: 8, opportunity: 6, size: 5 },
      { id: "3", name: "Voice search", value: 4, criticality: 3, opportunity: 4, size: 4 },
      { id: "4", name: "Loyalty coins", value: 7, criticality: 5, opportunity: 4, size: 3 },
      { id: "5", name: "Group ordering", value: 5, criticality: 4, opportunity: 6, size: 6 },
      { id: "6", name: "Referral rewards", value: 8, criticality: 6, opportunity: 5, size: 2 }
    ],
    calculate: (rows) => {
      const calculated = rows.map(row => {
        const val = Number(row.value) || 0;
        const crit = Number(row.criticality) || 0;
        const opp = Number(row.opportunity) || 0;
        const size = Number(row.size) || 1;
        const cod = val + crit + opp;
        const wsjf = Number((cod / size).toFixed(2));
        return { ...row, cod, wsjf };
      });
      // Sort and rank
      const sorted = [...calculated].sort((a, b) => b.wsjf - a.wsjf);
      return calculated.map(row => {
        const rank = sorted.findIndex(r => r.id === row.id) + 1;
        return { ...row, rank };
      });
    }
  },

  MoSCoW: {
    title: "MoSCoW Scoping",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Agreeing MVP or release scope with stakeholders. Makes trade-offs visible and consensual. Tag items M/S/C/W.",
    inPlainWords: "Split scope into Must / Should / Could / Won't - and keep Musts under ~60% of effort.",
    howToRun: [
      "Tag each backlog item M, S, C, or W with the team.",
      "Enter estimated effort (days) per item.",
      "Sum effort per category (Must, Should, Could, Won't).",
      "Verify Must-haves do not exceed 60% of total effort."
    ],
    watchOutFor: [
      "Everything becoming a 'Must' (nothing is prioritized).",
      "No 'Won't' list, so scope creep happens quietly.",
      "Musts eating all the slack, leaving no room for surprises."
    ],
    headers: [
      { key: "name", label: "Backlog Item", type: "text", editable: true },
      { key: "category", label: "Category (M/S/C/W)", type: "select", editable: true, options: ["M", "S", "C", "W"] },
      { key: "effort", label: "Effort (days)", type: "number", editable: true }
    ],
    defaultRows: [
      { id: "1", name: "Checkout works", category: "M", effort: 6 },
      { id: "2", name: "UPI payment", category: "M", effort: 5 },
      { id: "3", name: "Order tracking", category: "M", effort: 4 },
      { id: "4", name: "Loyalty coins", category: "S", effort: 3 },
      { id: "5", name: "Referral rewards", category: "S", effort: 3 },
      { id: "6", name: "Dark mode", category: "C", effort: 2 },
      { id: "7", name: "Voice search", category: "C", effort: 4 },
      { id: "8", name: "AR menu", category: "W", effort: 8 },
      { id: "9", name: "Group ordering", category: "S", effort: 5 }
    ],
    calculate: (rows) => {
      // MoSCoW doesn't change rows, but calculates stats
      const summary = { M: { count: 0, effort: 0 }, S: { count: 0, effort: 0 }, C: { count: 0, effort: 0 }, W: { count: 0, effort: 0 } };
      let totalEffort = 0;
      rows.forEach(r => {
        const cat = r.category || "W";
        const eff = Number(r.effort) || 0;
        if (summary[cat]) {
          summary[cat].count++;
          summary[cat].effort += eff;
        }
        totalEffort += eff;
      });
      return {
        rows,
        summary,
        totalEffort,
        mustPercent: totalEffort > 0 ? (summary["M"].effort / totalEffort) * 100 : 0
      };
    }
  },

  OpportunityScoring: {
    title: "Opportunity Scoring",
    stage: "Prioritisation",
    category: "Prioritisation",
    whenToUse: "Deciding which customer needs to invest in, grounded in survey data rather than the loudest stakeholder.",
    inPlainWords: "Attack outcomes users rate important but under-served: Opportunity = Importance + max(Importance - Satisfaction, 0).",
    howToRun: [
      "Survey users: rate outcome Importance and current Satisfaction (both 1-10).",
      "Opportunity = Importance + max(Importance - Satisfaction, 0).",
      "Rank outcomes. Score >10 is a strong signal, >15 is exceptional.",
      "Ignore over-served outcomes (low importance, high satisfaction)."
    ],
    watchOutFor: [
      "Confusing importance with what you think matters.",
      "Small or biased survey samples.",
      "Investing in already-satisfied outcomes because they're easy."
    ],
    headers: [
      { key: "name", label: "Desired Customer Outcome (Job Step)", type: "text", editable: true },
      { key: "importance", label: "Importance (1-10)", type: "number", editable: true },
      { key: "satisfaction", label: "Satisfaction (1-10)", type: "number", editable: true },
      { key: "opportunity", label: "Opportunity Score", type: "number", editable: false },
      { key: "rank", label: "Rank", type: "number", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Get food fast when hungry", importance: 9, satisfaction: 6 },
      { id: "2", name: "Trust the delivery ETA", importance: 9, satisfaction: 4 },
      { id: "3", name: "Pay without friction", importance: 8, satisfaction: 7 },
      { id: "4", name: "Discover new dishes I'll like", importance: 7, satisfaction: 3 },
      { id: "5", name: "Reorder favourites easily", importance: 8, satisfaction: 8 },
      { id: "6", name: "Resolve a wrong order", importance: 8, satisfaction: 3 }
    ],
    calculate: (rows) => {
      const calculated = rows.map(row => {
        const imp = Number(row.importance) || 0;
        const sat = Number(row.satisfaction) || 0;
        const opportunity = Number((imp + Math.max(imp - sat, 0)).toFixed(1));
        return { ...row, opportunity };
      });
      const sorted = [...calculated].sort((a, b) => b.opportunity - a.opportunity);
      return calculated.map(row => {
        const rank = sorted.findIndex(r => r.id === row.id) + 1;
        return { ...row, rank };
      });
    }
  },

  BCGMatrix: {
    title: "BCG Growth-Share Matrix",
    stage: "Strategy & Portfolio",
    category: "Strategy & Portfolio",
    whenToUse: "Allocating budget and product attention across multiple product lines during strategic portfolio reviews.",
    inPlainWords: "A portfolio view: fund Stars, milk Cash Cows, choose your Question Marks, exit Dogs.",
    howToRun: [
      "Plot each product line on Market Growth (Y-axis) and Relative Market Share (X-axis).",
      "Relative Market Share = your revenue/share divided by leading competitor's.",
      "Bubble size corresponds to annual revenue.",
      "Category splits at 10% market growth and 1.0x relative market share."
    ],
    watchOutFor: [
      "Wanting everything to be a Star - Cash Cows pay the bills.",
      "Defining 'the market' too broadly to flatter your share.",
      "Killing a Dog that quietly retains high-value users."
    ],
    headers: [
      { key: "name", label: "Product / Line", type: "text", editable: true },
      { key: "growth", label: "Market Growth (e.g. 0.15 = 15%)", type: "number", editable: true },
      { key: "share", label: "Relative Share (x)", type: "number", editable: true },
      { key: "revenue", label: "Revenue (₹ cr)", type: "number", editable: true },
      { key: "category", label: "Category", type: "text", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Food delivery", growth: 0.18, share: 1.4, revenue: 220 },
      { id: "2", name: "Grocery (10-min)", growth: 0.35, share: 0.6, revenue: 80 },
      { id: "3", name: "Dining-out coupons", growth: 0.05, share: 1.6, revenue: 140 },
      { id: "4", name: "Cloud kitchens", growth: 0.28, share: 0.5, revenue: 35 },
      { id: "5", name: "Ads / media", growth: 0.22, share: 1.2, revenue: 60 },
      { id: "6", name: "Print vouchers", growth: 0.02, share: 0.7, revenue: 12 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const growth = Number(row.growth) || 0;
        const share = Number(row.share) || 0;
        let category = "Dog";
        
        if (growth >= 0.1 && share >= 1.0) category = "★ Star";
        else if (growth >= 0.1 && share < 1.0) category = "? Question Mark";
        else if (growth < 0.1 && share >= 1.0) category = "$ Cash Cow";
        else category = "Dog";
        
        return { ...row, category };
      });
    }
  },

  AnsoffMatrix: {
    title: "Ansoff Growth Matrix",
    stage: "Strategy & Portfolio",
    category: "Strategy & Portfolio",
    whenToUse: "Choosing the direction of growth, and balancing a portfolio of safe vs ambitious growth bets.",
    inPlainWords: "Four ways to grow, in rising order of risk: market penetration, product dev, market dev, diversification.",
    howToRun: [
      "Classify each growth idea into one of the four quadrants.",
      "Estimate expected revenue and a success probability for each.",
      "Risk-adjusted value = Expected Revenue × Probability.",
      "Fund penetration for cash, back 1-2 adjacent bets, ring-fence diversification."
    ],
    watchOutFor: [
      "Over-indexing on diversification because it is exciting.",
      "Underestimating how different 'new market' execution really is.",
      "No probability weighting (makes risky bets look best)."
    ],
    headers: [
      { key: "name", label: "Initiative", type: "text", editable: true },
      { key: "vector", label: "Growth Vector", type: "select", editable: true, options: ["Market Penetration", "Product Development", "Market Development", "Diversification"] },
      { key: "revenue", label: "Expected Rev (₹ cr)", type: "number", editable: true },
      { key: "probability", label: "Success Prob. (%)", type: "percent", editable: true },
      { key: "riskAdjusted", label: "Risk-Adjusted Value", type: "number", editable: false }
    ],
    defaultRows: [
      { id: "1", name: "Push reorders to existing users", vector: "Market Penetration", revenue: 40, probability: 0.85 },
      { id: "2", name: "Launch grocery to same users", vector: "Product Development", revenue: 70, probability: 0.55 },
      { id: "3", name: "Enter Tier-3 towns", vector: "Market Development", revenue: 90, probability: 0.45 },
      { id: "4", name: "Fintech lending, new users", vector: "Diversification", revenue: 150, probability: 0.25 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const rev = Number(row.revenue) || 0;
        const prob = Number(row.probability) || 0;
        const riskAdjusted = Number((rev * prob).toFixed(1));
        return { ...row, riskAdjusted };
      });
    }
  },

  Porter5Forces: {
    title: "Porter's Five Forces",
    stage: "Strategy & Portfolio",
    category: "Strategy & Portfolio",
    whenToUse: "Deciding whether to enter a market, or explaining to leadership why industry margins are structurally low.",
    inPlainWords: "Industry profitability is set by five structural forces - know them before you enter.",
    howToRun: [
      "Score the threat level of each force from benign (1) to brutal (5) with evidence.",
      "Attractiveness = 6 - average score (higher is better).",
      "Identify the segment where forces are least hostile.",
      "Decide: compete on a moat, or don't enter."
    ],
    watchOutFor: [
      "Scoring optimistically to justify a decision already made.",
      "Treating the forces as static - funding and tech reshape forces fast.",
      "Forgetting complementors and regulation."
    ],
    headers: [
      { key: "force", label: "Force", type: "text", editable: false },
      { key: "threat", label: "Threat Level (1-5)", type: "number", editable: true },
      { key: "notes", label: "Notes / Evidence", type: "text", editable: true }
    ],
    defaultRows: [
      { id: "1", force: "Competitive rivalry", threat: 4, notes: "Zomato, Swiggy, local aggregators" },
      { id: "2", force: "Threat of new entrants", threat: 3, notes: "High capital + network effects deter, but funding is available" },
      { id: "3", force: "Supplier power (restaurants)", threat: 4, notes: "Large chains negotiate hard on commission" },
      { id: "4", force: "Buyer power (users)", threat: 4, notes: "Low switching cost, coupon-driven" },
      { id: "5", force: "Threat of substitutes", threat: 2, notes: "Home cooking, dine-in, direct restaurant apps" }
    ],
    calculate: (rows) => {
      let sum = 0;
      rows.forEach(r => {
        sum += Number(r.threat) || 0;
      });
      const avg = sum / 5;
      const attractiveness = 6 - avg;
      let verdict = "Hostile";
      if (attractiveness >= 4) verdict = "Attractive";
      else if (attractiveness >= 2.5) verdict = "Tough but viable";
      
      return {
        rows,
        avg: Number(avg.toFixed(2)),
        attractiveness: Number(attractiveness.toFixed(2)),
        verdict
      };
    }
  },

  NorthStar: {
    title: "North Star Metric",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Aligning product teams behind user value rather than vanity/revenue numbers.",
    inPlainWords: "One metric that captures the value customers get - decomposed into inputs a team can move.",
    howToRun: [
      "Choose an NSM that reflects delivered value (e.g. weekly on-time orders).",
      "Decompose it into inputs (Active users × Orders/user × On-time rate).",
      "Set 90-day targets and track % change.",
      "Track actual vs target monthly trajectory."
    ],
    watchOutFor: [
      "Picking revenue or signups (lagging / vanity) as the NSM.",
      "Too many 'north stars' - by definition there is only one.",
      "An NSM users can game without getting value."
    ],
    defaultDrivers: [
      { id: "1", driver: "Monthly active users (000s)", current: 1200, target: 1500 },
      { id: "2", driver: "Orders per user / month", current: 3.2, target: 3.8 },
      { id: "3", driver: "On-time delivery rate (%)", current: 0.86, target: 0.93 }
    ],
    defaultTrajectory: [
      { month: "Jan", actual: 2.90, target: 3.00 },
      { month: "Feb", actual: 3.00, target: 3.15 },
      { month: "Mar", actual: 3.15, target: 3.30 },
      { month: "Apr", actual: 3.30, target: 3.45 },
      { month: "May", actual: 3.40, target: 3.60 },
      { month: "Jun", actual: 3.55, target: 3.75 },
      { month: "Jul", actual: 3.70, target: 3.90 },
      { month: "Aug", actual: 3.80, target: 4.05 },
      { month: "Sep", actual: null, target: 4.20 }
    ],
    calculate: (drivers, trajectory) => {
      const calcDrivers = drivers.map(d => {
        const curr = Number(d.current) || 0;
        const targ = Number(d.target) || 0;
        const change = Number((targ - curr).toFixed(3));
        const pct = curr > 0 ? Number((change / curr).toFixed(4)) : 0;
        return { ...d, change, pct };
      });
      
      // Calculate NSM actual and target
      const getNSM = (active, orders, rate) => (active * orders * rate) / 1000;
      
      const nsmCurrent = getNSM(calcDrivers[0].current, calcDrivers[1].current, calcDrivers[2].current);
      const nsmTarget = getNSM(calcDrivers[0].target, calcDrivers[1].target, calcDrivers[2].target);
      const nsmChange = nsmTarget - nsmCurrent;
      const nsmPct = nsmCurrent > 0 ? nsmChange / nsmCurrent : 0;
      
      return {
        drivers: calcDrivers,
        trajectory,
        summary: {
          current: Number(nsmCurrent.toFixed(3)),
          target: Number(nsmTarget.toFixed(3)),
          change: Number(nsmChange.toFixed(3)),
          pct: Number(nsmPct.toFixed(4))
        }
      };
    }
  },

  AARRR: {
    title: "AARRR Pirate Funnel",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Diagnosing growth leaks and deciding where to focus product/onboarding optimization.",
    inPlainWords: "The lifecycle funnel: Acquisition, Activation, Retention, Revenue, Referral. Fix the leakiest step.",
    howToRun: [
      "Define one clear, trackable action for each stage.",
      "Enter the count of users reaching each stage.",
      "Step conversion = current stage users / previous stage users.",
      "Fix the funnel bottom-up (retention first)."
    ],
    watchOutFor: [
      "Optimising Acquisition while Activation/Retention are leaking.",
      "Vanity metrics (downloads) instead of activation/retention.",
      "Ignoring referral (the cheapest growth channel)."
    ],
    headers: [
      { key: "stage", label: "Funnel Stage", type: "text", editable: false },
      { key: "users", label: "Users", type: "number", editable: true },
      { key: "stepConv", label: "Step Conversion", type: "percent", editable: false },
      { key: "totalConv", label: "% of Acquisition", type: "percent", editable: false },
      { key: "definition", label: "Definition", type: "text", editable: false }
    ],
    defaultRows: [
      { id: "1", stage: "Acquisition", users: 100000, definition: "People who arrive at the landing page" },
      { id: "2", stage: "Activation", users: 42000, definition: "Hit the 'aha' moment (completed first order)" },
      { id: "3", stage: "Retention", users: 23000, definition: "Active after 30 days" },
      { id: "4", stage: "Revenue", users: 18000, definition: "Ordered again / pay subscription" },
      { id: "5", stage: "Referral", users: 5400, definition: "Invited a new friend" }
    ],
    calculate: (rows) => {
      const acq = Number(rows[0].users) || 1;
      return rows.map((row, idx) => {
        const users = Number(row.users) || 0;
        let stepConv = 0;
        if (idx === 0) {
          stepConv = 1.0;
        } else {
          const prevUsers = Number(rows[idx - 1].users) || 1;
          stepConv = users / prevUsers;
        }
        const totalConv = users / acq;
        return {
          ...row,
          stepConv: Number(stepConv.toFixed(4)),
          totalConv: Number(totalConv.toFixed(4))
        };
      });
    }
  },

  HEART: {
    title: "Google HEART Scorecard",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Measuring UX quality of a specific feature or workflow, capturing what business metrics miss.",
    inPlainWords: "Turn 'good UX' into trackable numbers across Happiness, Engagement, Adoption, Retention, Task success.",
    howToRun: [
      "Select the 2-3 HEART categories that matter for the feature.",
      "For each, write the Goal, the Signal, and the Metric.",
      "Enter Current and Target values.",
      "% Attainment = Current ÷ Target."
    ],
    watchOutFor: [
      "Tracking all five categories when only two matter.",
      "Measuring the easy signal instead of the true one.",
      "No Goal-Signal-Metric connection."
    ],
    headers: [
      { key: "category", label: "UX Category", type: "text", editable: false },
      { key: "metric", label: "Signal Metric", type: "text", editable: true },
      { key: "current", label: "Current", type: "number", editable: true },
      { key: "target", label: "Target Goal", type: "number", editable: true },
      { key: "attainment", label: "% Attainment", type: "percent", editable: false }
    ],
    defaultRows: [
      { id: "1", category: "Happiness", metric: "CSAT / app store rating", current: 0.72, target: 0.85 },
      { id: "2", category: "Engagement", metric: "Orders per active user / week", current: 1.1, target: 1.5 },
      { id: "3", category: "Adoption", metric: "New-feature uptake in 30d", current: 0.34, target: 0.60 },
      { id: "4", category: "Retention", metric: "D30 retention rate", current: 0.28, target: 0.40 },
      { id: "5", category: "Task success", metric: "Checkout completion rate", current: 0.88, target: 0.95 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const curr = Number(row.current) || 0;
        const targ = Number(row.target) || 1; // avoid division by zero
        const attainment = curr / targ;
        return {
          ...row,
          attainment: Number(attainment.toFixed(4))
        };
      });
    }
  },

  UnitEconomics: {
    title: "Unit Economics",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Before scaling marketing spend; investor discussions; or sanity-checking growth plans.",
    inPlainWords: "Does each customer make more than they cost? Aim for LTV:CAC ≥ 3 and payback ≤ 12 months.",
    howToRun: [
      "Compute contribution per user = ARPU × Gross Margin.",
      "Estimate customer lifetime = 1 ÷ Monthly Churn.",
      "LTV = Contribution × Lifetime; CAC = Spend ÷ Acquisitions.",
      "Evaluate payback and ratio; cut churn/CAC if unhealthy."
    ],
    watchOutFor: [
      "Using revenue instead of gross margin in LTV (overstates it).",
      "Ignoring the compounding effect of monthly churn.",
      "Under-counting CAC (omitting salaries, tools, discounts)."
    ],
    inputs: {
      arpu: 300,
      margin: 0.3,
      churn: 0.05,
      cac: 900
    },
    calculate: (inputs) => {
      const arpu = Number(inputs.arpu) || 0;
      const margin = Number(inputs.margin) || 0;
      const churn = Number(inputs.churn) || 0.01;
      const cac = Number(inputs.cac) || 1;
      
      const lifetime = 1 / churn;
      const contribution = arpu * margin;
      const ltv = contribution * lifetime;
      const ratio = ltv / cac;
      const payback = contribution > 0 ? cac / contribution : 999;
      
      let verdict = "Losing money";
      if (ratio >= 3.0) verdict = "Healthy ✓";
      else if (ratio >= 1.0) verdict = "Fragile";
      
      // Churn sensitivity
      const sensitivityRates = [0.03, 0.05, 0.07, 0.10];
      const sensitivity = sensitivityRates.map(r => {
        const sensLtv = contribution * (1 / r);
        const sensRatio = sensLtv / cac;
        return {
          churn: r,
          ltv: Number(sensLtv.toFixed(1)),
          ratio: Number(sensRatio.toFixed(2))
        };
      });
      
      return {
        inputs,
        lifetime: Number(lifetime.toFixed(1)),
        contribution: Number(contribution.toFixed(2)),
        ltv: Number(ltv.toFixed(2)),
        ratio: Number(ratio.toFixed(2)),
        payback: Number(payback.toFixed(1)),
        verdict,
        sensitivity
      };
    }
  },

  CohortRetention: {
    title: "Cohort Retention heatmap",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Judging real product stickiness and product-market fit. Watching whether product improvements retain newer cohorts higher.",
    inPlainWords: "Group users by when they joined and watch each cohort over time - the truest read on stickiness.",
    howToRun: [
      "Define cohort key (signup month) and the retention action.",
      "Calculate active users remaining at Month 1 to Month 6.",
      "Retention % = M_N / M_0.",
      "Look for curve flattening ( PMF signal )."
    ],
    watchOutFor: [
      "Reading a blended average that hides a leaky onboarding.",
      "Too-small cohorts resulting in noise.",
      "Declaring PMF before the curve flattens."
    ],
    headers: ["Cohort", "Size (M0)", "M1", "M2", "M3", "M4", "M5", "M6"],
    defaultRows: [
      { id: "1", cohort: "Jan", size: 5000, m1: 2900, m2: 2100, m3: 1750, m4: 1550, m5: 1450, m6: 1400 },
      { id: "2", cohort: "Feb", size: 5600, m1: 3360, m2: 2520, m3: 2130, m4: 1900, m5: 1800, m6: 1750 },
      { id: "3", cohort: "Mar", size: 6100, m1: 3900, m2: 3050, m3: 2620, m4: 2380, m5: 2260, m6: 2200 },
      { id: "4", cohort: "Apr", size: 6800, m1: 4490, m2: 3600, m3: 3130, m4: 2860, m5: 2720, m6: 2650 }
    ],
    calculate: (rows) => {
      const calculated = rows.map(r => {
        const size = Number(r.size) || 1;
        return {
          id: r.id,
          cohort: r.cohort,
          size,
          m0: 1.0,
          m1: Number((r.m1 / size).toFixed(4)),
          m2: Number((r.m2 / size).toFixed(4)),
          m3: Number((r.m3 / size).toFixed(4)),
          m4: Number((r.m4 / size).toFixed(4)),
          m5: Number((r.m5 / size).toFixed(4)),
          m6: Number((r.m6 / size).toFixed(4))
        };
      });
      
      // Calculate Average column
      const averages = { m0: 1.0, m1: 0, m2: 0, m3: 0, m4: 0, m5: 0, m6: 0 };
      const len = calculated.length || 1;
      calculated.forEach(c => {
        averages.m1 += c.m1;
        averages.m2 += c.m2;
        averages.m3 += c.m3;
        averages.m4 += c.m4;
        averages.m5 += c.m5;
        averages.m6 += c.m6;
      });
      
      Object.keys(averages).forEach(k => {
        if (k !== 'm0') averages[k] = Number((averages[k] / len).toFixed(4));
      });
      
      return {
        rows,
        calculated,
        averages
      };
    }
  },

  PMFSurvey: {
    title: "Sean Ellis PMF Survey",
    stage: "Metrics & Growth",
    category: "Metrics & Growth",
    whenToUse: "Gauging whether you have product-market fit before pouring fuel on scaling marketing spend.",
    inPlainWords: "If ≥40% of users would be 'very disappointed' to lose your product, you likely have PMF.",
    howToRun: [
      "Survey active users with the Sean Ellis question.",
      "Exclude N/A users who no longer use the product.",
      "PMF Score = Very Disappointed / Valid Responses.",
      "Score ≥ 40% means you are ready to scale."
    ],
    watchOutFor: [
      "Surveying signups who never reached the 'aha' moment.",
      "Chasing the 40% number instead of understanding user personas.",
      "Declaring PMF from one good week; confirm with retention."
    ],
    defaultRows: [
      { key: "very", label: "Very disappointed", count: 210 },
      { key: "somewhat", label: "Somewhat disappointed", count: 180 },
      { key: "not", label: "Not disappointed", count: 90 },
      { key: "na", label: "N/A – I no longer use it", count: 20 }
    ],
    calculate: (rows) => {
      const very = Number(rows.find(r => r.key === 'very').count) || 0;
      const somewhat = Number(rows.find(r => r.key === 'somewhat').count) || 0;
      const not = Number(rows.find(r => r.key === 'not').count) || 0;
      const na = Number(rows.find(r => r.key === 'na').count) || 0;
      
      const valid = very + somewhat + not;
      const score = valid > 0 ? very / valid : 0;
      
      let verdict = "Not yet";
      if (score >= 0.40) verdict = "PMF signal ✓";
      else if (score >= 0.25) verdict = "Getting there";
      
      return {
        rows,
        valid,
        score: Number(score.toFixed(4)),
        verdict
      };
    }
  },

  ABTest: {
    title: "A/B Test Significance",
    stage: "Experiments",
    category: "Experiments & Goals",
    whenToUse: "High-traffic decisions where a real, measurable metric can settle a debate objectively.",
    inPlainWords: "Let controlled experiments, not opinions, decide - but design them so the result is real, not noise.",
    howToRun: [
      "Form one clear hypothesis and pick a single primary metric.",
      "Enter visitors and conversions for Control and Variant.",
      "Calculate conversion rates, relative uplift, and z-score.",
      "Evaluate statistical significance (95% at z>1.96, 99% at z>2.576)."
    ],
    watchOutFor: [
      "Peeking and stopping early the moment it looks positive.",
      "Testing many things at once, preventing attribution.",
      "Confusing statistical significance with practical significance."
    ],
    inputs: {
      controlVisitors: 20000,
      controlConversions: 1600,
      variantVisitors: 20000,
      variantConversions: 1760
    },
    calculate: (inputs) => {
      const cv = Number(inputs.controlVisitors) || 1;
      const cc = Number(inputs.controlConversions) || 0;
      const vv = Number(inputs.variantVisitors) || 1;
      const vc = Number(inputs.variantConversions) || 0;
      
      const cr = cc / cv;
      const vr = vc / vv;
      const diff = vr - cr;
      const uplift = cr > 0 ? (vr / cr) - 1 : 0;
      
      const pooled = (cc + vc) / (cv + vv);
      const se = Math.sqrt(pooled * (1 - pooled) * (1 / cv + 1 / vv));
      
      const zScore = se > 0 ? diff / se : 0;
      const sig95 = Math.abs(zScore) > 1.96 ? "YES ✓" : "No";
      const sig99 = Math.abs(zScore) > 2.576 ? "YES ✓" : "No";
      
      return {
        inputs,
        cr: Number(cr.toFixed(4)),
        vr: Number(vr.toFixed(4)),
        diff: Number(diff.toFixed(4)),
        uplift: Number(uplift.toFixed(4)),
        se: Number(se.toFixed(6)),
        zScore: Number(zScore.toFixed(3)),
        sig95,
        sig99
      };
    }
  },

  OKRTracker: {
    title: "OKR Tracker",
    stage: "Experiments",
    category: "Experiments & Goals",
    whenToUse: "Quarterly goal-setting and mid-cycle check-ins across product teams.",
    inPlainWords: "An ambitious, qualitative Objective proven by 2-4 measurable Key Results.",
    howToRun: [
      "Set 1-3 Objectives per team - memorable, time-boxed.",
      "Attach 2-4 Key Results that are measurable and outcome-based.",
      "Set Start, Current, and Target values for each KR.",
      "Progress = (Current - Start) ÷ (Target - Start). Success is ~70%."
    ],
    watchOutFor: [
      "Writing task lists disguised as KRs.",
      "Too many OKRs - focus dies past ~3 objectives.",
      "Tying 100%-or-bust bonuses to stretch goals, killing ambition."
    ],
    headers: [
      { key: "objective", label: "Objective", type: "text", editable: true },
      { key: "kr", label: "Key Result", type: "text", editable: true },
      { key: "start", label: "Start", type: "number", editable: true },
      { key: "current", label: "Current", type: "number", editable: true },
      { key: "target", label: "Target Goal", type: "number", editable: true },
      { key: "progress", label: "Progress", type: "percent", editable: false },
      { key: "status", label: "Status", type: "text", editable: false }
    ],
    defaultRows: [
      { id: "1", objective: "Make ordering effortless", kr: "D30 retention (%)", start: 28, current: 33, target: 40 },
      { id: "2", objective: "Make ordering effortless", kr: "Checkout completion (%)", start: 88, current: 92, target: 95 },
      { id: "3", objective: "Win Tier-2 metros", kr: "Tier-2 MAU (000s)", start: 300, current: 520, target: 800 },
      { id: "4", objective: "Win Tier-2 metros", kr: "Tier-2 orders/user/mo", start: 2.1, current: 2.6, target: 3.2 },
      { id: "5", objective: "Fund growth sustainably", kr: "LTV:CAC ratio", start: 1.8, current: 2.4, target: 3.0 }
    ],
    calculate: (rows) => {
      return rows.map(row => {
        const start = Number(row.start) || 0;
        const curr = Number(row.current) || 0;
        const target = Number(row.target) || 0;
        
        let progress = 0;
        if (target !== start) {
          progress = (curr - start) / (target - start);
          progress = Math.max(0, Math.min(1, progress)); // Clamp between 0 and 1
        }
        
        let status = "Off track";
        if (progress >= 0.70) status = "On track";
        else if (progress >= 0.40) status = "At risk";
        
        return {
          ...row,
          progress: Number(progress.toFixed(4)),
          status
        };
      });
    }
  }
};
"""

with open('frontend/src/utils/pmTemplates.js', 'w', encoding='utf-8') as f:
    f.write(templates_js)

print("Generated frontend/src/utils/pmTemplates.js successfully!")
