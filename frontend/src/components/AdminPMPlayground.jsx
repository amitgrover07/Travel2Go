import React, { useState, useEffect, useRef } from 'react';
import * as d3 from 'd3';
import { 
  Sliders, 
  ArrowLeft, 
  RotateCcw, 
  Trash2, 
  Download, 
  Plus, 
  X, 
  BookOpen, 
  TrendingUp, 
  Target, 
  Info, 
  Search, 
  CheckCircle2, 
  Activity,
  Maximize2
} from 'lucide-react';
import toast from 'react-hot-toast';
import { PM_TEMPLATES } from '../utils/pmTemplates';

// ============================================================================
// --- CENTRAL STATE TOOLTIP CONTEXT ---
// ============================================================================
// A single floating HTML tooltip component.
const Tooltip = ({ tooltip }) => {
  if (!tooltip.show) return null;
  return (
    <div 
      className="absolute bg-slate-900 text-slate-100 p-3 rounded-lg shadow-xl text-xs pointer-events-none z-[100] border border-slate-800 max-w-xs transition-all duration-75 select-none"
      style={{ left: tooltip.x + 15, top: tooltip.y - 20 }}
      dangerouslySetInnerHTML={{ __html: tooltip.content }}
    />
  );
};

// ============================================================================
// --- CUSTOM D3 CHART COMPONENTS ---
// ============================================================================

// 1. RICE / WSJF / Opportunity Bar Chart (Vertical Bars)
const RiceBarChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 20, right: 30, bottom: 40, left: 110 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);
    const sortedData = [...data].sort((a, b) => b.score - a.score);

    const xScale = d3.scaleLinear()
      .domain([0, d3.max(sortedData, d => d.score) || 100])
      .range([0, chartWidth]);

    const yScale = d3.scaleBand()
      .domain(sortedData.map(d => d.name))
      .range([0, chartHeight])
      .padding(0.25);

    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text")
      .attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale))
      .attr("color", "#cbd5e1")
      .selectAll("text")
      .attr("font-size", "10px")
      .attr("font-weight", "500")
      .attr("fill", "#334155");

    const grad = svg.append("defs")
      .append("linearGradient")
      .attr("id", "riceGrad")
      .attr("x1", "0%").attr("y1", "0%")
      .attr("x2", "100%").attr("y2", "0%");
    grad.append("stop").attr("offset", "0%").attr("stop-color", "#818cf8");
    grad.append("stop").attr("offset", "100%").attr("stop-color", "#4f46e5");

    g.selectAll(".bar")
      .data(sortedData)
      .enter()
      .append("rect")
      .attr("class", "bar cursor-help transition-all duration-100")
      .attr("y", d => yScale(d.name))
      .attr("x", 0)
      .attr("height", yScale.bandwidth())
      .attr("width", d => xScale(d.score))
      .attr("fill", "url(#riceGrad)")
      .attr("rx", 4)
      .on("mouseover", (event, d) => {
        d3.select(event.currentTarget).attr("fill", "#312e81");
        setTooltip({
          show: true,
          x: event.pageX,
          y: event.pageY,
          content: `<strong>${d.name}</strong><br/>Score: ${Math.round(d.score)}<br/>Rank: #${d.rank || '-'}`
        });
      })
      .on("mousemove", (event) => {
        setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
      })
      .on("mouseout", (event) => {
        d3.select(event.currentTarget).attr("fill", "url(#riceGrad)");
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      });

    g.selectAll(".label")
      .data(sortedData)
      .enter()
      .append("text")
      .attr("class", "pointer-events-none")
      .attr("y", d => yScale(d.name) + yScale.bandwidth() / 2 + 4)
      .attr("x", d => Math.min(xScale(d.score) + 5, chartWidth - 35))
      .attr("fill", d => xScale(d.score) > chartWidth - 50 ? "#fff" : "#1e293b")
      .attr("font-size", "9px")
      .attr("font-weight", "bold")
      .text(d => Math.round(d.score));

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-3 uppercase tracking-wider">Prioritisation Scoreboard</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 2. Weighted Scoring Bar Chart
const WeightedBarChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 20, right: 30, bottom: 40, left: 110 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);
    const sortedData = [...data].sort((a, b) => b.score - a.score);

    const xScale = d3.scaleLinear().domain([0, 5]).range([0, chartWidth]);
    const yScale = d3.scaleBand().domain(sortedData.map(d => d.name)).range([0, chartHeight]).padding(0.25);

    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text")
      .attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale))
      .attr("color", "#cbd5e1")
      .selectAll("text")
      .attr("font-size", "10px")
      .attr("fill", "#334155");

    const grad = svg.append("defs")
      .append("linearGradient")
      .attr("id", "weightedGrad")
      .attr("x1", "0%").attr("y1", "0%")
      .attr("x2", "100%").attr("y2", "0%");
    grad.append("stop").attr("offset", "0%").attr("stop-color", "#06b6d4");
    grad.append("stop").attr("offset", "100%").attr("stop-color", "#0891b2");

    g.selectAll(".bar")
      .data(sortedData)
      .enter()
      .append("rect")
      .attr("class", "cursor-help")
      .attr("y", d => yScale(d.name))
      .attr("x", 0)
      .attr("height", yScale.bandwidth())
      .attr("width", d => xScale(d.score || 0))
      .attr("fill", "url(#weightedGrad)")
      .attr("rx", 4)
      .on("mouseover", (event, d) => {
        d3.select(event.currentTarget).attr("fill", "#0f766e");
        setTooltip({
          show: true,
          x: event.pageX,
          y: event.pageY,
          content: `<strong>${d.name}</strong><br/>Weighted Total: ${d.score?.toFixed(2)}`
        });
      })
      .on("mousemove", (event) => {
        setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
      })
      .on("mouseout", (event) => {
        d3.select(event.currentTarget).attr("fill", "url(#weightedGrad)");
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      });

    g.selectAll(".label")
      .data(sortedData)
      .enter()
      .append("text")
      .attr("class", "pointer-events-none")
      .attr("y", d => yScale(d.name) + yScale.bandwidth() / 2 + 4)
      .attr("x", d => xScale(d.score || 0) + 5)
      .attr("fill", "#1e293b")
      .attr("font-size", "10px")
      .attr("font-weight", "bold")
      .text(d => d.score?.toFixed(2));

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-3 uppercase tracking-wider">Weighted Performance</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 3. Value vs Effort (2x2 DRAGGABLE Scatter Plot)
const ValueVsEffortChart = ({ data, onUpdateRow, setTooltip }) => {
  const svgRef = useRef(null);
  const dataRef = useRef(data);
  dataRef.current = data;

  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 320;
    const margin = { top: 25, right: 30, bottom: 40, left: 45 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const xScale = d3.scaleLinear().domain([0, 10]).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, 10]).range([chartHeight, 0]);

    // Background quadrant fills
    g.append("rect")
      .attr("x", xScale(0)).attr("y", yScale(10))
      .attr("width", xScale(5) - xScale(0))
      .attr("height", yScale(5) - yScale(10))
      .attr("fill", "#22c55e").attr("opacity", 0.06);

    g.append("rect")
      .attr("x", xScale(5)).attr("y", yScale(10))
      .attr("width", xScale(10) - xScale(5))
      .attr("height", yScale(5) - yScale(10))
      .attr("fill", "#3b82f6").attr("opacity", 0.06);

    g.append("rect")
      .attr("x", xScale(0)).attr("y", yScale(5))
      .attr("width", xScale(5) - xScale(0))
      .attr("height", yScale(0) - yScale(5))
      .attr("fill", "#64748b").attr("opacity", 0.06);

    g.append("rect")
      .attr("x", xScale(5)).attr("y", yScale(5))
      .attr("width", xScale(10) - xScale(5))
      .attr("height", yScale(0) - yScale(5))
      .attr("fill", "#ef4444").attr("opacity", 0.06);

    // Divider Lines
    g.append("line")
      .attr("x1", xScale(5)).attr("y1", yScale(0))
      .attr("x2", xScale(5)).attr("y2", yScale(10))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    g.append("line")
      .attr("x1", xScale(0)).attr("y1", yScale(5))
      .attr("x2", xScale(10)).attr("y2", yScale(5))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    // Quadrant titles
    g.append("text").attr("x", xScale(2.5)).attr("y", yScale(9.4)).attr("text-anchor", "middle").attr("fill", "#15803d").attr("font-size", "10px").attr("font-weight", "bold").text("Quick Wins");
    g.append("text").attr("x", xScale(7.5)).attr("y", yScale(9.4)).attr("text-anchor", "middle").attr("fill", "#1d4ed8").attr("font-size", "10px").attr("font-weight", "bold").text("Big Bets");
    g.append("text").attr("x", xScale(2.5)).attr("y", yScale(0.6)).attr("text-anchor", "middle").attr("fill", "#475569").attr("font-size", "10px").attr("font-weight", "bold").text("Fill-ins");
    g.append("text").attr("x", xScale(7.5)).attr("y", yScale(0.6)).attr("text-anchor", "middle").attr("fill", "#b91c1c").attr("font-size", "10px").attr("font-weight", "bold").text("Money Pits");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(10))
      .attr("color", "#94a3b8");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(10))
      .attr("color", "#94a3b8");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Effort (1-10) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Value (1-10) →");

    // Drag behaviour
    const dragHandler = d3.drag()
      .on("start", (event) => {
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      })
      .on("drag", function(event, d) {
        const coords = d3.pointer(event, g.node());
        // Map pointer position back to values clamped between 0.5 and 10
        const rawEffort = Math.max(0.5, Math.min(10, xScale.invert(coords[0])));
        const rawValue = Math.max(0.5, Math.min(10, yScale.invert(coords[1])));
        
        // Visual updates in DOM for drag responsiveness
        d3.select(this).select("circle")
          .attr("cx", xScale(rawEffort))
          .attr("cy", yScale(rawValue))
          .attr("fill", "#d946ef"); // color during drag

        d3.select(this).select("text")
          .attr("x", xScale(rawEffort) + 9)
          .attr("y", yScale(rawValue) + 4);
      })
      .on("end", function(event, d) {
        const coords = d3.pointer(event, g.node());
        const effort = Math.max(1, Math.min(10, Math.round(xScale.invert(coords[0]))));
        const value = Math.max(1, Math.min(10, Math.round(yScale.invert(coords[1]))));
        
        // Restore standard dot coloring
        const defaultColor = (value >= 5 && effort < 5) ? "#22c55e" :
                            (value >= 5 && effort >= 5) ? "#3b82f6" :
                            (value < 5 && effort < 5) ? "#64748b" : "#ef4444";
        
        d3.select(this).select("circle").attr("fill", defaultColor);
        onUpdateRow(d.id, { effort, value });
      });

    // Plot groups
    const dots = g.selectAll(".dot-group")
      .data(data)
      .enter()
      .append("g")
      .attr("class", "dot-group cursor-grab active:cursor-grabbing")
      .call(dragHandler);

    dots.append("circle")
      .attr("cx", d => xScale(Number(d.effort) || 0))
      .attr("cy", d => yScale(Number(d.value) || 0))
      .attr("r", 7)
      .attr("fill", d => {
        const val = Number(d.value) || 0;
        const eff = Number(d.effort) || 0;
        if (val >= 5 && eff < 5) return "#22c55e";
        if (val >= 5 && eff >= 5) return "#3b82f6";
        if (val < 5 && eff < 5) return "#64748b";
        return "#ef4444";
      })
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5);

    dots.append("text")
      .attr("x", d => xScale(Number(d.effort) || 0) + 9)
      .attr("y", d => yScale(Number(d.value) || 0) + 4)
      .text(d => d.name)
      .attr("font-size", "9px")
      .attr("font-weight", "700")
      .attr("fill", "#334155");

    // Add tooltips to dots
    dots.on("mouseover", (event, d) => {
      setTooltip({
        show: true,
        x: event.pageX,
        y: event.pageY,
        content: `<strong>${d.name}</strong><br/>Value: ${d.value}/10<br/>Effort: ${d.effort}/10<br/><span class="text-indigo-400 font-bold">Drag dot to edit!</span>`
      });
    })
    .on("mousemove", (event) => {
      setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
    })
    .on("mouseout", () => {
      setTooltip({ show: false, x: 0, y: 0, content: "" });
    });

  }, [data, onUpdateRow, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <div className="flex items-center gap-1.5 mb-2">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Value vs Effort Interactive 2x2</span>
        <span className="text-[9px] bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded text-indigo-600 font-bold uppercase animate-pulse">Drag Dots to Edit Table</span>
      </div>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 4. Kano Model Better vs Worse Scatter Plot
const KanoScatterPlot = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 320;
    const margin = { top: 25, right: 30, bottom: 40, left: 45 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const xScale = d3.scaleLinear().domain([-1, 0]).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, 1]).range([chartHeight, 0]);

    // Draw dividers
    g.append("line")
      .attr("x1", xScale(-0.5)).attr("y1", yScale(0))
      .attr("x2", xScale(-0.5)).attr("y2", yScale(1))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "3,3");

    g.append("line")
      .attr("x1", xScale(-1)).attr("y1", yScale(0.5))
      .attr("x2", xScale(0)).attr("y2", yScale(0.5))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "3,3");

    // Grid labels
    g.append("text").attr("x", xScale(-0.25)).attr("y", yScale(0.85)).attr("text-anchor", "middle").attr("fill", "#6366f1").attr("font-size", "10px").attr("font-weight", "bold").text("Delighters");
    g.append("text").attr("x", xScale(-0.75)).attr("y", yScale(0.85)).attr("text-anchor", "middle").attr("fill", "#06b6d4").attr("font-size", "10px").attr("font-weight", "bold").text("Performance");
    g.append("text").attr("x", xScale(-0.75)).attr("y", yScale(0.15)).attr("text-anchor", "middle").attr("fill", "#e11d48").attr("font-size", "10px").attr("font-weight", "bold").text("Must-be");
    g.append("text").attr("x", xScale(-0.25)).attr("y", yScale(0.15)).attr("text-anchor", "middle").attr("fill", "#64748b").attr("font-size", "10px").attr("font-weight", "bold").text("Indifferent");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Worse Coefficient (Dissatisfaction) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Better Coefficient (Satisfaction) →");

    // Plot
    const dots = g.selectAll(".dot")
      .data(data)
      .enter()
      .append("g")
      .attr("class", "cursor-help");

    dots.append("circle")
      .attr("cx", d => xScale(Number(d.worse) || 0))
      .attr("cy", d => yScale(Number(d.better) || 0))
      .attr("r", 6.5)
      .attr("fill", d => {
        if (d.classification === "Delighter") return "#6366f1";
        if (d.classification === "Performance") return "#06b6d4";
        if (d.classification === "Must-be") return "#e11d48";
        return "#64748b";
      })
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5);

    dots.append("text")
      .attr("x", d => xScale(Number(d.worse) || 0) + 8)
      .attr("y", d => yScale(Number(d.better) || 0) + 3)
      .text(d => d.name)
      .attr("font-size", "9px")
      .attr("font-weight", "650")
      .attr("fill", "#334155");

    dots.on("mouseover", (event, d) => {
      setTooltip({
        show: true,
        x: event.pageX,
        y: event.pageY,
        content: `<strong>${d.name}</strong><br/>Category: <strong>${d.classification}</strong><br/>Better: ${d.better}<br/>Worse: ${d.worse}`
      });
    })
    .on("mousemove", (event) => {
      setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
    })
    .on("mouseout", () => {
      setTooltip({ show: false, x: 0, y: 0, content: "" });
    });

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-2 uppercase tracking-wider">Kano Better vs Worse Matrix</span>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 5. BCG Matrix (DRAGGABLE Bubble Chart)
const BCGBubbleChart = ({ data, onUpdateRow, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 320;
    const margin = { top: 25, right: 30, bottom: 40, left: 45 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const xScale = d3.scaleLinear().domain([0, 2.0]).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, 0.40]).range([chartHeight, 0]);
    const rScale = d3.scaleSqrt().domain([0, d3.max(data, d => d.revenue) || 100]).range([5, 25]);

    // Dividers
    g.append("line")
      .attr("x1", xScale(1.0)).attr("y1", yScale(0))
      .attr("x2", xScale(1.0)).attr("y2", yScale(0.40))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    g.append("line")
      .attr("x1", xScale(0)).attr("y1", yScale(0.10))
      .attr("x2", xScale(2.0)).attr("y2", yScale(0.10))
      .attr("stroke", "#cbd5e1").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    // Quad Labels
    g.append("text").attr("x", xScale(1.5)).attr("y", yScale(0.35)).attr("text-anchor", "middle").attr("fill", "#6366f1").attr("font-size", "11px").attr("font-weight", "bold").text("★ Star");
    g.append("text").attr("x", xScale(0.5)).attr("y", yScale(0.35)).attr("text-anchor", "middle").attr("fill", "#e11d48").attr("font-size", "11px").attr("font-weight", "bold").text("? Question Mark");
    g.append("text").attr("x", xScale(1.5)).attr("y", yScale(0.04)).attr("text-anchor", "middle").attr("fill", "#16a34a").attr("font-size", "11px").attr("font-weight", "bold").text("$ Cash Cow");
    g.append("text").attr("x", xScale(0.5)).attr("y", yScale(0.04)).attr("text-anchor", "middle").attr("fill", "#64748b").attr("font-size", "11px").attr("font-weight", "bold").text("Dog");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5).tickFormat(d => d + "x"))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5).tickFormat(d => (d * 100) + "%"))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Relative Market Share (x) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#64748b").text("Market Growth Rate (%) →");

    // Drag Listener
    const dragHandler = d3.drag()
      .on("start", () => {
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      })
      .on("drag", function(event, d) {
        const coords = d3.pointer(event, g.node());
        const rawShare = Math.max(0.1, Math.min(2.0, xScale.invert(coords[0])));
        const rawGrowth = Math.max(0.01, Math.min(0.40, yScale.invert(coords[1])));

        d3.select(this).select("circle")
          .attr("cx", xScale(rawShare))
          .attr("cy", yScale(rawGrowth))
          .attr("fill", "#d946ef");

        d3.select(this).select("text")
          .attr("x", xScale(rawShare))
          .attr("y", yScale(rawGrowth) + 4);
      })
      .on("end", function(event, d) {
        const coords = d3.pointer(event, g.node());
        const share = Number(Math.max(0.1, Math.min(2.0, xScale.invert(coords[0]))).toFixed(2));
        const growth = Number(Math.max(0.0, Math.min(0.40, yScale.invert(coords[1]))).toFixed(3));
        onUpdateRow(d.id, { share, growth });
      });

    // Bubbles
    const bubbles = g.selectAll(".bubble-group")
      .data(data)
      .enter()
      .append("g")
      .attr("class", "bubble-group cursor-grab active:cursor-grabbing")
      .call(dragHandler);

    bubbles.append("circle")
      .attr("cx", d => xScale(Number(d.share) || 0))
      .attr("cy", d => yScale(Number(d.growth) || 0))
      .attr("r", d => rScale(Number(d.revenue) || 0))
      .attr("fill", d => {
        if (d.category?.includes("Star")) return "#6366f1";
        if (d.category?.includes("Question Mark")) return "#e11d48";
        if (d.category?.includes("Cash Cow")) return "#16a34a";
        return "#64748b";
      })
      .attr("opacity", 0.75)
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5);

    bubbles.append("text")
      .attr("x", d => xScale(Number(d.share) || 0))
      .attr("y", d => yScale(Number(d.growth) || 0) + 4)
      .text(d => d.name)
      .attr("text-anchor", "middle")
      .attr("font-size", "8.5px")
      .attr("font-weight", "bold")
      .attr("fill", "#ffffff")
      .style("paint-order", "stroke")
      .style("stroke", "#1e293b")
      .style("stroke-width", "2.5px")
      .style("stroke-linejoin", "round");

    bubbles.on("mouseover", (event, d) => {
      setTooltip({
        show: true,
        x: event.pageX,
        y: event.pageY,
        content: `<strong>${d.name}</strong><br/>Market Growth: ${(d.growth * 100).toFixed(1)}%<br/>Mkt Share: ${d.share}x<br/>Revenue: ₹${d.revenue} cr<br/><span class="text-indigo-400 font-bold block mt-1">Drag bubble to edit!</span>`
      });
    })
    .on("mousemove", (event) => {
      setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
    })
    .on("mouseout", () => {
      setTooltip({ show: false, x: 0, y: 0, content: "" });
    });

  }, [data, onUpdateRow, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <div className="flex items-center gap-1.5 mb-2">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">BCG Bubble Matrix</span>
        <span className="text-[9px] bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded text-indigo-600 font-bold uppercase animate-pulse">Drag Bubbles to Edit</span>
      </div>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 6. AARRR Funnel Visual Chart
const AARRRFunnelChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 300;
    const margin = { top: 20, right: 30, bottom: 20, left: 55 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);
    const count = data.length;
    const rowHeight = chartHeight / count;

    data.forEach((d, idx) => {
      const topWidth = chartWidth * (idx === 0 ? 1.0 : Number(data[idx - 1].totalConv) || 0.1);
      const bottomWidth = chartWidth * (Number(d.totalConv) || 0.1);
      
      const yTop = idx * rowHeight;
      const yBottom = (idx + 1) * rowHeight - 6;

      const xTopStart = (chartWidth - topWidth) / 2;
      const xTopEnd = xTopStart + topWidth;
      const xBottomStart = (chartWidth - bottomWidth) / 2;
      const xBottomEnd = xBottomStart + bottomWidth;

      const pathString = `M ${xTopStart} ${yTop} L ${xTopEnd} ${yTop} L ${xBottomEnd} ${yBottom} L ${xBottomStart} ${yBottom} Z`;

      const blockColor = d3.interpolatePurples(0.3 + (idx * 0.14));

      g.append("path")
        .attr("d", pathString)
        .attr("fill", blockColor)
        .attr("opacity", 0.9)
        .attr("stroke", "#ffffff")
        .attr("stroke-width", 1.5)
        .attr("class", "cursor-help transition-all duration-100 hover:opacity-100")
        .on("mouseover", (event) => {
          setTooltip({
            show: true,
            x: event.pageX,
            y: event.pageY,
            content: `<strong>${d.stage}</strong><br/>Users: ${(d.users).toLocaleString()}<br/>Step Conv: ${idx === 0 ? '-' : (d.stepConv * 100).toFixed(1) + '%'}<br/>Conversion of Top: ${(d.totalConv * 100).toFixed(1)}%`
          });
        })
        .on("mousemove", (event) => {
          setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
        })
        .on("mouseout", () => {
          setTooltip({ show: false, x: 0, y: 0, content: "" });
        });

      // Label inside funnel
      g.append("text")
        .attr("class", "pointer-events-none")
        .attr("x", chartWidth / 2)
        .attr("y", yTop + rowHeight / 2 - 1)
        .attr("text-anchor", "middle")
        .attr("fill", "#ffffff")
        .attr("font-size", "11px")
        .attr("font-weight", "bold")
        .text(`${d.stage}: ${(d.users).toLocaleString()}`);

      if (idx > 0) {
        g.append("text")
          .attr("x", xTopStart - 10)
          .attr("y", yTop + 3)
          .attr("text-anchor", "end")
          .attr("fill", "#6d28d9")
          .attr("font-size", "10px")
          .attr("font-weight", "bold")
          .text(`↳ ${Math.round(d.stepConv * 100)}%`);
      }
    });

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-2 uppercase tracking-wider">AARRR Pirate Funnel Conversion</span>
      <svg ref={svgRef} width="450" height="300" className="max-w-full" />
    </div>
  );
};

// 7. A/B Test Rates Chart
const ABTestChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 30, right: 30, bottom: 40, left: 60 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const chartData = [
      { name: "Control", rate: data.cr, visitors: data.inputs.controlVisitors, conv: data.inputs.controlConversions },
      { name: "Variant", rate: data.vr, visitors: data.inputs.variantVisitors, conv: data.inputs.variantConversions }
    ];

    const maxRate = d3.max(chartData, d => d.rate) || 0.1;
    const yScale = d3.scaleLinear().domain([0, maxRate * 1.3]).range([chartHeight, 0]);
    const xScale = d3.scaleBand().domain(["Control", "Variant"]).range([0, chartWidth]).padding(0.45);

    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale).tickFormat(d3.format(".1%")))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.selectAll(".bar")
      .data(chartData)
      .enter()
      .append("rect")
      .attr("x", d => xScale(d.name))
      .attr("y", d => yScale(d.rate))
      .attr("width", xScale.bandwidth())
      .attr("height", d => chartHeight - yScale(d.rate))
      .attr("fill", d => d.name === "Control" ? "#64748b" : "#6366f1")
      .attr("rx", 5)
      .attr("class", "cursor-help hover:opacity-90")
      .on("mouseover", (event, d) => {
        setTooltip({
          show: true,
          x: event.pageX,
          y: event.pageY,
          content: `<strong>${d.name}</strong><br/>Conv. Rate: ${(d.rate * 100).toFixed(2)}%<br/>Visitors: ${d.visitors.toLocaleString()}<br/>Conversions: ${d.conv.toLocaleString()}`
        });
      })
      .on("mousemove", (event) => {
        setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
      })
      .on("mouseout", () => {
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      });

    // Error bars / confidence intervals (95%)
    chartData.forEach(d => {
      const p = d.rate;
      const n = d.visitors;
      if (n <= 0) return;
      const ciHalf = 1.96 * Math.sqrt((p * (1 - p)) / n);
      const lower = Math.max(0, p - ciHalf);
      const upper = p + ciHalf;

      const x = xScale(d.name) + xScale.bandwidth() / 2;

      g.append("line")
        .attr("x1", x).attr("y1", yScale(lower))
        .attr("x2", x).attr("y2", yScale(upper))
        .attr("stroke", "#1e293b")
        .attr("stroke-width", 1.5);

      g.append("line")
        .attr("x1", x - 6).attr("y1", yScale(upper))
        .attr("x2", x + 6).attr("y2", yScale(upper))
        .attr("stroke", "#1e293b")
        .attr("stroke-width", 1.5);

      g.append("line")
        .attr("x1", x - 6).attr("y1", yScale(lower))
        .attr("x2", x + 6).attr("y2", yScale(lower))
        .attr("stroke", "#1e293b")
        .attr("stroke-width", 1.5);
    });

    // Rate Labels
    g.selectAll(".label")
      .data(chartData)
      .enter()
      .append("text")
      .attr("class", "pointer-events-none")
      .attr("x", d => xScale(d.name) + xScale.bandwidth() / 2)
      .attr("y", d => yScale(d.rate) - 8)
      .attr("text-anchor", "middle")
      .attr("fill", "#1e293b")
      .attr("font-size", "11px")
      .attr("font-weight", "bold")
      .text(d => (d.rate * 100).toFixed(2) + "%");

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-2 uppercase tracking-wider">Conversion Comparison (95% CI error bars)</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 8. Custom D3 INTERACTIVE SLIDER Bar Chart for Porter's 5 Forces
const PorterForcesChart = ({ data, onUpdateRow, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 20, right: 30, bottom: 40, left: 135 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const xScale = d3.scaleLinear().domain([0, 5]).range([0, chartWidth]);
    const yScale = d3.scaleBand().domain(data.map(d => d.force)).range([0, chartHeight]).padding(0.3);

    // Draw background ticks
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale))
      .attr("color", "#cbd5e1")
      .selectAll("text")
      .attr("font-size", "9.5px")
      .attr("font-weight", "500")
      .attr("fill", "#334155")
      .each(function(d) {
        const text = d3.select(this);
        const nameShort = d.replace("Supplier power ", "Suppliers ").replace("Buyer power ", "Buyers ").replace("Threat of ", "Threat ");
        text.text(nameShort);
      });

    // Drag behaviour to increase/decrease threat
    const dragHandler = d3.drag()
      .on("drag", function(event, d) {
        const coords = d3.pointer(event, g.node());
        const rawThreat = Math.max(1, Math.min(5, xScale.invert(coords[0])));
        
        // Instant visual feedback
        d3.select(this).select("rect")
          .attr("width", xScale(rawThreat));
        
        d3.select(this).select("text")
          .attr("x", xScale(rawThreat) + 6)
          .text(rawThreat.toFixed(1));
      })
      .on("end", function(event, d) {
        const coords = d3.pointer(event, g.node());
        const threat = Math.max(1, Math.min(5, Math.round(xScale.invert(coords[0]))));
        
        const idx = data.findIndex(r => r.id === d.id);
        onUpdateRow(idx, { threat });
      });

    // Groups
    const bars = g.selectAll(".force-bar")
      .data(data)
      .enter()
      .append("g")
      .attr("class", "force-bar cursor-ew-resize select-none")
      .call(dragHandler);

    bars.append("rect")
      .attr("y", d => yScale(d.force))
      .attr("x", 0)
      .attr("height", yScale.bandwidth())
      .attr("width", d => xScale(Number(d.threat) || 0))
      .attr("fill", d => d.threat >= 4 ? "#ef4444" : d.threat >= 2.5 ? "#f59e0b" : "#10b981")
      .attr("rx", 3);

    bars.append("text")
      .attr("x", d => xScale(Number(d.threat) || 0) + 6)
      .attr("y", d => yScale(d.force) + yScale.bandwidth() / 2 + 3.5)
      .attr("fill", "#475569")
      .attr("font-size", "10px")
      .attr("font-weight", "bold")
      .text(d => Number(d.threat).toFixed(0));

    // Tooltips
    bars.on("mouseover", (event, d) => {
      setTooltip({
        show: true,
        x: event.pageX,
        y: event.pageY,
        content: `<strong>${d.force}</strong><br/>Threat: ${d.threat}/5<br/>Notes: ${d.notes || 'None'}<br/><span class="text-indigo-400 font-bold block mt-1">Drag bar horizontal to edit!</span>`
      });
    })
    .on("mousemove", (event) => {
      setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
    })
    .on("mouseout", () => {
      setTooltip({ show: false, x: 0, y: 0, content: "" });
    });

  }, [data, onUpdateRow, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <div className="flex items-center gap-1.5 mb-2">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Five Forces Interactive Bars</span>
        <span className="text-[9px] bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded text-indigo-600 font-bold uppercase animate-pulse">Drag Bars to Edit</span>
      </div>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 9. Custom D3 Donut Chart with Exploding Slices (Replaces MoSCoW Pie Chart)
const MoscowDonutChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!data) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const radius = Math.min(width, height) / 2 - 20;

    const g = svg.append("g")
      .attr("transform", `translate(${width / 2}, ${height / 2})`);

    const chartData = [
      { key: "Must-have", val: data.summary.M.effort, count: data.summary.M.count, color: "#ef4444" },
      { key: "Should-have", val: data.summary.S.effort, count: data.summary.S.count, color: "#3b82f6" },
      { key: "Could-have", val: data.summary.C.effort, count: data.summary.C.count, color: "#10b981" },
      { key: "Won't-have", val: data.summary.W.effort, count: data.summary.W.count, color: "#94a3b8" }
    ].filter(d => d.val > 0);

    const pie = d3.pie().value(d => d.val).sort(null);
    const arc = d3.arc().innerRadius(radius * 0.45).outerRadius(radius);
    const hoverArc = d3.arc().innerRadius(radius * 0.45).outerRadius(radius + 8);

    const paths = g.selectAll(".slice")
      .data(pie(chartData))
      .enter()
      .append("g")
      .attr("class", "slice cursor-help");

    paths.append("path")
      .attr("d", arc)
      .attr("fill", d => d.data.color)
      .attr("stroke", "#ffffff")
      .attr("stroke-width", 2)
      .on("mouseover", function(event, d) {
        d3.select(this)
          .transition().duration(150)
          .attr("d", hoverArc);
        
        const pct = data.totalEffort > 0 ? (d.data.val / data.totalEffort * 100).toFixed(0) : 0;
        setTooltip({
          show: true,
          x: event.pageX,
          y: event.pageY,
          content: `<strong>${d.data.key}</strong><br/>Effort: ${d.data.val} days (${pct}%)<br/>Items: ${d.data.count}`
        });
      })
      .on("mousemove", (event) => {
        setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
      })
      .on("mouseout", function() {
        d3.select(this)
          .transition().duration(150)
          .attr("d", arc);
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      });

    // Add labels outside donut
    paths.append("text")
      .attr("transform", d => `translate(${arc.centroid(d)})`)
      .attr("dy", "0.35em")
      .attr("text-anchor", "middle")
      .attr("fill", "#ffffff")
      .attr("font-size", "10px")
      .attr("font-weight", "bold")
      .text(d => d.data.val > 2 ? `${d.data.val}d` : "");

    // Center text total effort
    g.append("text")
      .attr("text-anchor", "middle")
      .attr("dy", "-0.2em")
      .attr("fill", "#64748b")
      .attr("font-size", "9px")
      .attr("font-weight", "bold")
      .text("TOTAL EFFORT");

    g.append("text")
      .attr("text-anchor", "middle")
      .attr("dy", "1em")
      .attr("fill", "#1e293b")
      .attr("font-size", "16px")
      .attr("font-weight", "900")
      .text(`${data.totalEffort} days`);

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-2 uppercase tracking-wider">Moscow Effort Donut Distribution</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 10. Custom D3 Line Chart with Interactive Mouse Tracker Focus Rule (Replaces North Star Google Line Chart)
const NorthStarLineChart = ({ trajectory, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!trajectory) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 20, right: 35, bottom: 40, left: 45 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    // filter valid elements
    const validData = trajectory.map((t, i) => ({
      month: t.month,
      idx: i,
      actual: t.actual !== null ? Number(t.actual) : null,
      target: t.target !== null ? Number(t.target) : null
    }));

    const maxVal = d3.max(validData, d => Math.max(d.actual || 0, d.target || 0)) || 5.0;
    const xScale = d3.scalePoint().domain(validData.map(d => d.month)).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, maxVal * 1.15]).range([chartHeight, 0]);

    // Grid lines horizontal
    g.append("g")
      .attr("class", "grid-lines opacity-10")
      .call(d3.axisLeft(yScale).ticks(5).tickSize(-chartWidth).tickFormat(""));

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    // Line calculators
    const lineActual = d3.line()
      .defined(d => d.actual !== null)
      .x(d => xScale(d.month))
      .y(d => yScale(d.actual));

    const lineTarget = d3.line()
      .defined(d => d.target !== null)
      .x(d => xScale(d.month))
      .y(d => yScale(d.target));

    // Area actual
    const areaActual = d3.area()
      .defined(d => d.actual !== null)
      .x(d => xScale(d.month))
      .y0(chartHeight)
      .y1(d => yScale(d.actual));

    // Draw target line
    g.append("path")
      .datum(validData)
      .attr("fill", "none")
      .attr("stroke", "#94a3b8")
      .attr("stroke-dasharray", "4,4")
      .attr("stroke-width", 2)
      .attr("d", lineTarget);

    // Draw actual area & line
    g.append("path")
      .datum(validData)
      .attr("fill", "url(#nsAreaGrad)")
      .attr("opacity", 0.15)
      .attr("d", areaActual);

    g.append("path")
      .datum(validData)
      .attr("fill", "none")
      .attr("stroke", "#6366f1")
      .attr("stroke-width", 3)
      .attr("d", lineActual);

    // Gradient definition
    const defs = svg.append("defs");
    const areaGrad = defs.append("linearGradient")
      .attr("id", "nsAreaGrad")
      .attr("x1", "0%").attr("y1", "0%")
      .attr("x2", "0%").attr("y2", "100%");
    areaGrad.append("stop").attr("offset", "0%").attr("stop-color", "#6366f1");
    areaGrad.append("stop").attr("offset", "100%").attr("stop-color", "#ffffff");

    // Trajectory Hover overlay elements
    const focusLine = g.append("line")
      .attr("y1", 0).attr("y2", chartHeight)
      .attr("stroke", "#475569")
      .attr("stroke-width", 1.5)
      .attr("stroke-dasharray", "3,3")
      .style("display", "none");

    const circleActual = g.append("circle")
      .attr("r", 5)
      .attr("fill", "#6366f1")
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5)
      .style("display", "none");

    const circleTarget = g.append("circle")
      .attr("r", 5)
      .attr("fill", "#94a3b8")
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5)
      .style("display", "none");

    // Capture mouse moves
    svg.append("rect")
      .attr("width", chartWidth)
      .attr("height", chartHeight)
      .attr("transform", `translate(${margin.left}, ${margin.top})`)
      .attr("fill", "none")
      .attr("pointer-events", "all")
      .on("mouseover", () => {
        focusLine.style("display", null);
        circleActual.style("display", null);
        circleTarget.style("display", null);
      })
      .on("mousemove", (event) => {
        const mouseX = d3.pointer(event)[0] - margin.left;
        
        // Find closest point by dividing width
        const step = chartWidth / (validData.length - 1);
        const closestIdx = Math.max(0, Math.min(validData.length - 1, Math.round(mouseX / step)));
        
        const pt = validData[closestIdx];
        const xPos = xScale(pt.month);

        focusLine.attr("x1", xPos).attr("x2", xPos);
        
        if (pt.actual !== null) {
          circleActual.attr("cx", xPos).attr("cy", yScale(pt.actual)).style("display", null);
        } else {
          circleActual.style("display", "none");
        }
        
        if (pt.target !== null) {
          circleTarget.attr("cx", xPos).attr("cy", yScale(pt.target)).style("display", null);
        } else {
          circleTarget.style("display", "none");
        }

        setTooltip({
          show: true,
          x: event.pageX,
          y: event.pageY,
          content: `<strong>Month: ${pt.month}</strong><br/>Actual: ${pt.actual !== null ? pt.actual.toFixed(2) + ' mn' : '—'}<br/>Target: ${pt.target !== null ? pt.target.toFixed(2) + ' mn' : '—'}`
        });
      })
      .on("mouseout", () => {
        focusLine.style("display", "none");
        circleActual.style("display", "none");
        circleTarget.style("display", "none");
        setTooltip({ show: false, x: 0, y: 0, content: "" });
      });

  }, [trajectory, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <span className="text-xs font-bold text-slate-500 mb-2 uppercase tracking-wider">NSM Trajectory (Interactive Focus line)</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 11. Custom D3 Multi-line Curves with Curve Highlighting (Replaces Cohort Curves Google Line Chart)
const CohortRetentionChart = ({ data, setTooltip }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!data || !data.calculated) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 280;
    const margin = { top: 25, right: 90, bottom: 40, left: 45 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;

    const g = svg.append("g").attr("transform", `translate(${margin.left}, ${margin.top})`);

    const months = ["M0", "M1", "M2", "M3", "M4", "M5", "M6"];
    const xScale = d3.scalePoint().domain(months).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, 100]).range([chartHeight, 0]);

    // Prepare line data structures
    const cohorts = data.calculated.map(c => ({
      name: `${c.cohort} Cohort`,
      color: c.cohort === "Jan" ? "#c084fc" : c.cohort === "Feb" ? "#818cf8" : c.cohort === "Mar" ? "#38bdf8" : "#fb7185",
      points: months.map((m, idx) => ({
        month: m,
        val: c[`m${idx}`] * 100
      }))
    }));

    // Add Average line
    cohorts.push({
      name: "Average Curve",
      color: "#1e293b",
      isAverage: true,
      points: months.map((m, idx) => ({
        month: m,
        val: data.averages[`m${idx}`] * 100
      }))
    });

    // Grid lines horizontal
    g.append("g")
      .attr("class", "grid-lines opacity-10")
      .call(d3.axisLeft(yScale).ticks(5).tickSize(-chartWidth).tickFormat(""));

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5).tickFormat(d => d + "%"))
      .attr("color", "#cbd5e1")
      .selectAll("text").attr("fill", "#64748b");

    const lineGen = d3.line()
      .x(d => xScale(d.month))
      .y(d => yScale(d.val))
      .curve(d3.curveMonotoneX);

    // Draw cohort curves
    const paths = g.selectAll(".cohort-path")
      .data(cohorts)
      .enter()
      .append("path")
      .attr("class", "cohort-path cursor-pointer transition-all duration-150")
      .attr("fill", "none")
      .attr("stroke", d => d.color)
      .attr("stroke-width", d => d.isAverage ? 3 : 2)
      .attr("stroke-dasharray", d => d.isAverage ? "3,3" : "none")
      .attr("opacity", 0.7)
      .attr("d", d => lineGen(d.points));

    // Draw interactive legend on the right side
    const legend = g.selectAll(".cohort-legend")
      .data(cohorts)
      .enter()
      .append("g")
      .attr("class", "cohort-legend cursor-pointer")
      .attr("transform", (d, idx) => `translate(${chartWidth + 10}, ${idx * 20 + 20})`);

    legend.append("rect")
      .attr("width", 10).attr("height", 10)
      .attr("fill", d => d.color)
      .attr("rx", 2);

    legend.append("text")
      .attr("x", 15).attr("y", 9)
      .attr("fill", "#475569")
      .attr("font-size", "9px")
      .attr("font-weight", "bold")
      .text(d => d.name);

    // Hover Highlight trigger behavior
    const triggerHighlight = (hoveredName) => {
      paths.transition().duration(150)
        .attr("opacity", d => d.name === hoveredName ? 1.0 : 0.12)
        .attr("stroke-width", d => d.name === hoveredName ? 3.5 : 1.5);
    };

    const resetHighlight = () => {
      paths.transition().duration(150)
        .attr("opacity", 0.7)
        .attr("stroke-width", d => d.isAverage ? 3 : 2);
    };

    // Attach highlights
    legend.on("mouseover", (event, d) => triggerHighlight(d.name))
          .on("mouseout", resetHighlight);

    paths.on("mouseover", function(event, d) {
      triggerHighlight(d.name);
      
      const ptsDesc = d.points.map(pt => `${pt.month}: ${Math.round(pt.val)}%`).join(" | ");
      setTooltip({
        show: true,
        x: event.pageX,
        y: event.pageY,
        content: `<strong>${d.name}</strong><br/><span class="font-mono text-[10px] text-indigo-200">${ptsDesc}</span>`
      });
    })
    .on("mousemove", (event) => {
      setTooltip(prev => ({ ...prev, x: event.pageX, y: event.pageY }));
    })
    .on("mouseout", () => {
      resetHighlight();
      setTooltip({ show: false, x: 0, y: 0, content: "" });
    });

  }, [data, setTooltip]);

  return (
    <div className="bg-white border border-slate-200 p-4 rounded-xl flex flex-col items-center shadow-sm w-full">
      <div className="flex items-center gap-1.5 mb-2">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Retention Performance (Curves)</span>
        <span className="text-[9px] bg-purple-50 border border-purple-200 px-2 py-0.5 rounded text-purple-600 font-bold uppercase animate-pulse">Hover Curves/Legend to Highlight</span>
      </div>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};


// ============================================================================
// --- MAIN PLAYGROUND CONTAINER ---
// ============================================================================

const AdminPMPlayground = () => {
  const [activeTab, setActiveTab] = useState('index'); // 'index' | templateKey (e.g. 'RICE')
  const [searchQuery, setSearchQuery] = useState('');
  const [tooltip, setTooltip] = useState({ show: false, x: 0, y: 0, content: "" });

  // Initialise state from localStorage or load defaults
  const [frameworkData, setFrameworkData] = useState(() => {
    const saved = localStorage.getItem('travel2go_pm_playground');
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        const merged = {};
        Object.keys(PM_TEMPLATES).forEach(key => {
          merged[key] = parsed[key] || {
            rows: PM_TEMPLATES[key].defaultRows ? [...PM_TEMPLATES[key].defaultRows] : null,
            inputs: PM_TEMPLATES[key].inputs ? { ...PM_TEMPLATES[key].inputs } : null,
            weights: PM_TEMPLATES[key].weights ? { ...PM_TEMPLATES[key].weights } : null,
            drivers: PM_TEMPLATES[key].defaultDrivers ? [...PM_TEMPLATES[key].defaultDrivers] : null,
            trajectory: PM_TEMPLATES[key].defaultTrajectory ? [...PM_TEMPLATES[key].defaultTrajectory] : null,
          };
        });
        return merged;
      } catch (e) {
        console.error("Failed to parse PM Playground localStorage", e);
      }
    }
    
    // Default load
    const initial = {};
    Object.keys(PM_TEMPLATES).forEach(key => {
      initial[key] = {
        rows: PM_TEMPLATES[key].defaultRows ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultRows)) : null,
        inputs: PM_TEMPLATES[key].inputs ? { ...PM_TEMPLATES[key].inputs } : null,
        weights: PM_TEMPLATES[key].weights ? { ...PM_TEMPLATES[key].weights } : null,
        drivers: PM_TEMPLATES[key].defaultDrivers ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultDrivers)) : null,
        trajectory: PM_TEMPLATES[key].defaultTrajectory ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultTrajectory)) : null,
      };
    });
    return initial;
  });

  // Helper deep copy
  function jsonCopy(obj) {
    return JSON.stringify(obj);
  }

  // Save changes to localStorage
  useEffect(() => {
    localStorage.setItem('travel2go_pm_playground', JSON.stringify(frameworkData));
  }, [frameworkData]);

  // Actions
  const handleReset = (key) => {
    if (!window.confirm("Are you sure you want to restore the default example values for this framework?")) return;
    setFrameworkData(prev => ({
      ...prev,
      [key]: {
        rows: PM_TEMPLATES[key].defaultRows ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultRows)) : null,
        inputs: PM_TEMPLATES[key].inputs ? { ...PM_TEMPLATES[key].inputs } : null,
        weights: PM_TEMPLATES[key].weights ? { ...PM_TEMPLATES[key].weights } : null,
        drivers: PM_TEMPLATES[key].defaultDrivers ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultDrivers)) : null,
        trajectory: PM_TEMPLATES[key].defaultTrajectory ? JSON.parse(jsonCopy(PM_TEMPLATES[key].defaultTrajectory)) : null,
      }
    }));
    toast.success("Template data restored successfully!");
  };

  const handleClear = (key) => {
    if (!window.confirm("Are you sure you want to clear all rows/inputs?")) return;
    setFrameworkData(prev => {
      const cleared = { ...prev[key] };
      if (cleared.rows) {
        cleared.rows = [];
      }
      if (cleared.inputs) {
        Object.keys(cleared.inputs).forEach(k => {
          cleared.inputs[k] = 0;
        });
      }
      if (cleared.weights) {
        Object.keys(cleared.weights).forEach(k => {
          cleared.weights[k] = 0;
        });
      }
      if (cleared.drivers) {
        cleared.drivers = cleared.drivers.map(d => ({ ...d, current: 0, target: 0 }));
      }
      if (cleared.trajectory) {
        cleared.trajectory = cleared.trajectory.map(t => ({ ...t, actual: null, target: null }));
      }
      return { ...prev, [key]: cleared };
    });
    toast.success("Grid cleared!");
  };

  const handleExportCSV = (key) => {
    const template = PM_TEMPLATES[key];
    const data = frameworkData[key];
    if (!data || (!data.rows && !data.inputs)) return;

    let csvContent = "data:text/csv;charset=utf-8,";
    csvContent += `PM Playground Assessment - ${template.title}\n\n`;

    if (data.rows) {
      // Write headers
      const headers = template.headers.map(h => h.label);
      csvContent += headers.join(",") + "\n";
      
      // Calculate final rows
      const finalRows = template.calculate ? template.calculate(data.rows, data.weights) : data.rows;
      finalRows.forEach(row => {
        const line = template.headers.map(h => row[h.key]);
        csvContent += line.join(",") + "\n";
      });
    } else if (data.inputs) {
      Object.keys(data.inputs).forEach(k => {
        csvContent += `${k},${data.inputs[k]}\n`;
      });
    }

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", `${key}_assessment.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    toast.success("CSV file exported!");
  };

  // Editable Grid Cell Updates (Direct mutation callbacks)
  const handleCellChange = (frameworkKey, rowIndex, colKey, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedRows = [...framework.rows];
      updatedRows[rowIndex] = { ...updatedRows[rowIndex], [colKey]: val };
      framework.rows = updatedRows;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  // Chart drag callbacks
  const handleRowDataUpdate = (frameworkKey, rowId, updatedFields) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedRows = framework.rows.map(r => 
        r.id === rowId ? { ...r, ...updatedFields } : r
      );
      framework.rows = updatedRows;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handlePorterRowUpdate = (rowIndex, updatedFields) => {
    setFrameworkData(prev => {
      const framework = { ...prev["Porter5Forces"] };
      const updatedRows = [...framework.rows];
      updatedRows[rowIndex] = { ...updatedRows[rowIndex], ...updatedFields };
      framework.rows = updatedRows;
      return { ...prev, ["Porter5Forces"]: framework };
    });
  };

  const cleanAndClampInput = (val, min, max) => {
    if (val === '' || val === undefined || val === null) return 0;
    let str = val.toString();
    // Remove leading zeros from integers (e.g. '05' -> '5') but preserve decimals (like '0.5')
    if (str.length > 1 && str.startsWith('0') && str[1] !== '.') {
      str = str.replace(/^0+/, '');
    }
    let num = Number(str);
    if (isNaN(num)) num = 0;
    if (min !== undefined && num < min) num = min;
    if (max !== undefined && num > max) num = max;
    return num;
  };

  const handleWeightChange = (frameworkKey, weightKey, val) => {
    const cleaned = cleanAndClampInput(val, 0, 100);
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      framework.weights = { ...framework.weights, [weightKey]: cleaned };
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleInputChange = (frameworkKey, inputKey, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const inputs = { ...framework.inputs };
      
      let min = 0;
      let max = undefined;
      if (frameworkKey === "UnitEconomics") {
        if (inputKey === "margin" || inputKey === "churn") max = 100;
      } else if (frameworkKey === "ABTest") {
        if (inputKey === "controlVisitors" || inputKey === "variantVisitors") min = 1;
        if (inputKey === "controlConversions") max = inputs.controlVisitors || 1;
        if (inputKey === "variantConversions") max = inputs.variantVisitors || 1;
      }
      
      const cleaned = cleanAndClampInput(val, min, max);
      inputs[inputKey] = cleaned;
      framework.inputs = inputs;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleDriverChange = (frameworkKey, idx, field, val) => {
    let min = 0;
    let max = undefined;
    if (idx === 2) { // On-time delivery rate (%)
      max = 100;
    }
    const cleaned = cleanAndClampInput(val, min, max);
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedDrivers = [...framework.drivers];
      updatedDrivers[idx] = { ...updatedDrivers[idx], [field]: cleaned };
      framework.drivers = updatedDrivers;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleTrajectoryChange = (frameworkKey, idx, field, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedTrajectory = [...framework.trajectory];
      
      let cleanVal = val;
      if (val === '') {
        cleanVal = null;
      } else {
        let str = val.toString();
        if (str.length > 1 && str.startsWith('0') && str[1] !== '.') {
          str = str.replace(/^0+/, '');
        }
        cleanVal = Number(str);
        if (isNaN(cleanVal)) cleanVal = null;
        if (cleanVal < 0) cleanVal = 0;
      }
      
      updatedTrajectory[idx] = { 
        ...updatedTrajectory[idx], 
        [field]: cleanVal 
      };
      framework.trajectory = updatedTrajectory;
      return { ...prev, [frameworkKey]: framework };
    });
  };


  // Row Manipulation
  const handleAddRow = (frameworkKey) => {
    const template = PM_TEMPLATES[frameworkKey];
    const newId = String(Date.now());
    const newRow = { id: newId };
    
    template.headers.forEach(h => {
      if (h.editable) {
        if (h.type === "number" || h.type === "percent") newRow[h.key] = 0;
        else if (h.key === "category") newRow[h.key] = "M";
        else if (h.key === "vector") newRow[h.key] = "Market Penetration";
        else newRow[h.key] = "";
      }
    });

    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      framework.rows = [...framework.rows, newRow];
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleDeleteRow = (frameworkKey, idx) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      framework.rows = framework.rows.filter((_, i) => i !== idx);
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const groups = [
    {
      name: "Prioritisation backlogs",
      icon: <Sliders className="h-4 w-4 text-purple-600" />,
      color: "purple",
      keys: ["RICE", "WeightedScoring", "ValueVsEffort", "KanoModel", "WSJF", "MoSCoW", "OpportunityScoring"]
    },
    {
      name: "Strategy & Portfolios",
      icon: <TrendingUp className="h-4 w-4 text-blue-600" />,
      color: "blue",
      keys: ["BCGMatrix", "AnsoffMatrix", "Porter5Forces"]
    },
    {
      name: "Metrics & Growth loops",
      icon: <Activity className="h-4 w-4 text-emerald-600" />,
      color: "emerald",
      keys: ["NorthStar", "AARRR", "HEART", "UnitEconomics", "CohortRetention", "PMFSurvey"]
    },
    {
      name: "Experiments & OKRs",
      icon: <Target className="h-4 w-4 text-rose-600" />,
      color: "rose",
      keys: ["ABTest", "OKRTracker"]
    }
  ];

  const allFrameworksList = [];
  groups.forEach(g => {
    g.keys.forEach(k => {
      allFrameworksList.push({
        key: k,
        ...PM_TEMPLATES[k],
        groupName: g.name,
        groupColor: g.color
      });
    });
  });

  return (
    <div className="w-full bg-slate-50 min-h-screen text-slate-800 rounded-xl overflow-hidden shadow-sm flex border border-slate-200 relative select-none">
      
      {/* --- PLAYGROUND LEFT NAV SIDEBAR (LIGHT MODE) --- */}
      <aside className="w-64 border-r border-slate-200 bg-white flex flex-col shrink-0">
        <div className="p-4 border-b border-slate-200 flex items-center gap-2">
          <div className="p-2 rounded-lg bg-pink-50 border border-pink-200 text-pink-600">
            <Activity className="h-5 w-5 animate-pulse" />
          </div>
          <div>
            <h2 className="font-bold text-sm text-slate-800">PM Playground</h2>
            <p className="text-[10px] text-slate-400 font-bold">Sandbox Management</p>
          </div>
        </div>

        {/* Sidebar Search */}
        <div className="p-3">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
            <input
              type="text"
              placeholder="Search frameworks..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 rounded-lg pl-8 pr-3 py-1.5 text-xs text-slate-700 placeholder-slate-400 focus:outline-none focus:border-pink-500/50"
            />
          </div>
        </div>

        {/* Navigation list */}
        <nav className="flex-1 overflow-y-auto px-2 space-y-4 pb-4">
          <button
            onClick={() => setActiveTab('index')}
            className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-bold transition-colors ${
              activeTab === 'index' 
                ? 'bg-pink-50 text-pink-600 border border-pink-100' 
                : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50'
            }`}
          >
            <BookOpen className="h-4 w-4" />
            <span>Dashboard Home</span>
          </button>

          {groups.map((g, idx) => (
            <div key={idx} className="space-y-1">
              <span className="px-3 text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-1">
                {g.name}
              </span>
              {g.keys.map(k => {
                const template = PM_TEMPLATES[k];
                const active = activeTab === k;
                return (
                  <button
                    key={k}
                    onClick={() => setActiveTab(k)}
                    className={`w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-left text-xs transition-all ${
                      active 
                        ? 'bg-slate-100 text-pink-600 font-bold border-l-2 border-pink-500 pl-4' 
                        : 'text-slate-500 hover:text-slate-800 hover:bg-slate-50 pl-3'
                    }`}
                  >
                    <span className="truncate">{template.title}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </nav>
      </aside>

      {/* --- PLAYGROUND MAIN SECTION (LIGHT MODE) --- */}
      <section className="flex-1 flex flex-col bg-slate-50 min-w-0">
        
        {/* TOP BAR */}
        <header className="h-14 border-b border-slate-200 px-6 flex items-center justify-between shrink-0 bg-white shadow-sm">
          <div className="flex items-center gap-3">
            {activeTab !== 'index' && (
              <button 
                onClick={() => setActiveTab('index')} 
                className="p-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-500 hover:text-slate-850 transition-colors"
                title="Back to Dashboard"
              >
                <ArrowLeft size={16} />
              </button>
            )}
            <h1 className="font-extrabold text-base text-slate-800">
              {activeTab === 'index' ? "PM Playbook Dashboard" : PM_TEMPLATES[activeTab].title}
            </h1>
          </div>
          
          {activeTab !== 'index' && (
            <div className="flex items-center gap-2">
              <button
                onClick={() => handleReset(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-white border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-650 hover:text-slate-800 transition-all shadow-sm"
              >
                <RotateCcw size={13} />
                <span>Reset to Example</span>
              </button>
              <button
                onClick={() => handleClear(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-rose-50 border border-rose-100 hover:bg-rose-100/50 text-xs font-bold text-rose-600 hover:text-rose-700 transition-all shadow-sm"
              >
                <Trash2 size={13} />
                <span>Clear All</span>
              </button>
              <button
                onClick={() => handleExportCSV(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-white border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-655 hover:text-slate-800 transition-all shadow-sm"
              >
                <Download size={13} />
                <span>Export CSV</span>
              </button>
            </div>
          )}
        </header>

        {/* CONTAINER CONTENT */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          
          {/* ==================================================================
              VIEW 1: INDEX DASHBOARD
              ================================================================== */}
          {activeTab === 'index' && (
            <>
              {/* Header hero section */}
              <div className="relative p-6 rounded-2xl bg-gradient-to-r from-pink-500/5 via-indigo-500/5 to-white border border-slate-200 overflow-hidden shadow-sm">
                <div className="relative z-10 max-w-2xl">
                  <span className="text-[10px] uppercase font-bold text-pink-600 tracking-wider bg-pink-100 px-2.5 py-1 rounded border border-pink-200">
                    A Senior Consultant's Field Guide
                  </span>
                  <h2 className="text-2xl font-black text-slate-800 mt-3 mb-2">The Product Manager's Framework Playbook</h2>
                  <p className="text-slate-500 text-xs leading-relaxed font-semibold">
                    Welcome to the Quantitative Assessment Playground. Choose from 18 active formula-based calculators below. Plug in your own figures (yellow fields in sheets) or use the prefilled templates to test your product economics, prioritise backlogs, and run experiments.
                  </p>
                </div>
                <div className="absolute right-0 bottom-0 top-0 w-1/3 bg-[radial-gradient(circle_at_right,#f43f5e10,#00000000)]" />
              </div>

              {/* Grid of groups */}
              <div className="space-y-6">
                {groups.map((g, groupIdx) => {
                  const matches = g.keys.filter(k => {
                    const temp = PM_TEMPLATES[k];
                    return temp.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           temp.whenToUse.toLowerCase().includes(searchQuery.toLowerCase());
                  });
                  
                  if (matches.length === 0) return null;

                  return (
                    <div key={groupIdx} className="space-y-3">
                      <div className="flex items-center gap-2 border-b border-slate-200 pb-2">
                        {g.icon}
                        <h3 className="font-bold text-xs uppercase tracking-wider text-slate-500">
                          {g.name}
                        </h3>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {matches.map(k => {
                          const template = PM_TEMPLATES[k];
                          return (
                            <div
                              key={k}
                              onClick={() => setActiveTab(k)}
                              className="group p-5 rounded-xl bg-white border border-slate-200 hover:border-pink-500/40 hover:shadow-md transition-all duration-150 cursor-pointer flex flex-col justify-between hover:translate-y-[-2px]"
                            >
                              <div>
                                <div className="flex justify-between items-center mb-2">
                                  <span className={`text-[9px] font-bold px-2 py-0.5 rounded uppercase tracking-wider ${
                                    g.color === 'purple' ? 'bg-purple-50 text-purple-600 border border-purple-100' :
                                    g.color === 'blue' ? 'bg-blue-50 text-blue-600 border border-blue-100' :
                                    g.color === 'emerald' ? 'bg-emerald-50 text-emerald-600 border border-emerald-100' :
                                    'bg-rose-50 text-rose-600 border border-rose-100'
                                  }`}>
                                    {template.stage}
                                  </span>
                                  <span className="text-[10px] text-slate-400 font-mono">Excel Calculator</span>
                                </div>
                                <h4 className="font-extrabold text-sm text-slate-800 group-hover:text-pink-600 transition-colors">
                                  {template.title}
                                </h4>
                                <p className="text-slate-500 text-xs mt-2 line-clamp-3 leading-relaxed">
                                  {template.whenToUse}
                                </p>
                              </div>
                              <div className="mt-4 pt-3 border-t border-slate-100 flex justify-between items-center">
                                <span className="text-[10px] text-slate-400 font-bold group-hover:text-slate-600 transition-colors">
                                  Open Calculator →
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )}

          {/* ==================================================================
              VIEW 2: INDIVIDUAL FRAMEWORK CALCULATOR
              ================================================================== */}
          {activeTab !== 'index' && (() => {
            const template = PM_TEMPLATES[activeTab];
            const data = frameworkData[activeTab];

            return (
              <div className="space-y-6">
                
                {/* 1. GUIDELINES INFOGRAPHIC PANEL */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Left Column: Descriptions */}
                  <div className="space-y-4">
                    {/* In Plain Words */}
                    <div className="p-4 rounded-xl bg-gradient-to-br from-indigo-50/50 to-purple-50/50 border border-indigo-100">
                      <span className="text-[9px] font-bold uppercase tracking-wider text-indigo-600 flex items-center gap-1">
                        <Info size={11} /> In Plain Words
                      </span>
                      <p className="text-xs text-indigo-900 mt-1 font-bold leading-relaxed">
                        {template.inPlainWords}
                      </p>
                    </div>

                    {/* When to use */}
                    <div className="p-4 rounded-xl bg-white border border-slate-200">
                      <span className="text-[9px] font-bold uppercase tracking-wider text-blue-600 block mb-1">
                        When to Reach for It
                      </span>
                      <p className="text-xs text-slate-600 leading-relaxed font-medium">
                        {template.whenToUse}
                      </p>
                    </div>
                  </div>

                  {/* Right Column: Steps & Watch outs */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {/* How to run */}
                    <div className="p-4 rounded-xl bg-white border border-slate-200 flex flex-col justify-between">
                      <div>
                        <span className="text-[9px] font-bold uppercase tracking-wider text-emerald-650 block mb-2">
                          How to Run It
                        </span>
                        <ol className="text-[11px] text-slate-650 space-y-1.5 list-decimal pl-4 leading-normal font-medium">
                          {template.howToRun.map((step, sIdx) => (
                            <li key={sIdx}>{step}</li>
                          ))}
                        </ol>
                      </div>
                    </div>

                    {/* Watch out for */}
                    <div className="p-4 rounded-xl bg-rose-50 border border-rose-100 flex flex-col justify-between">
                      <div>
                        <span className="text-[9px] font-bold uppercase tracking-wider text-rose-600 block mb-2">
                          Watch Out For (Traps)
                        </span>
                        <ul className="text-[11px] text-rose-700 space-y-1.5 list-disc pl-4 leading-normal font-medium">
                          {template.watchOutFor.map((trap, tIdx) => (
                            <li key={tIdx}>{trap}</li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 2. DYNAMIC INPUT & CALCULATION GRID */}
                <div className="p-5 rounded-xl bg-white border border-slate-200 space-y-4 shadow-sm">
                  <div className="flex justify-between items-center border-b border-slate-100 pb-3">
                    <span className="font-bold text-sm text-slate-800">Assessment Workspace</span>
                    {template.headers && (
                      <button
                        onClick={() => handleAddRow(activeTab)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded bg-pink-600 hover:bg-pink-700 text-xs font-semibold text-white transition-colors shadow-sm"
                      >
                        <Plus size={14} />
                        <span>Add Row</span>
                      </button>
                    )}
                  </div>

                  {/* GRID EDITING WORKSPACE */}
                  {(() => {
                    if (activeTab === "NorthStar") {
                      return (
                        <div className="space-y-6">
                          {/* Part 1: Input Drivers */}
                          <div>
                            <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider mb-2">
                              1. Inputs & Drivers Decomposition
                            </h4>
                            <div className="overflow-x-auto">
                              <table className="w-full text-xs text-left border border-slate-200 rounded-lg overflow-hidden">
                                <thead className="bg-slate-50 text-slate-600 font-bold border-b border-slate-200">
                                  <tr>
                                    <th className="p-3">Input Driver</th>
                                    <th className="p-3 bg-yellow-500/10 text-yellow-750">Current</th>
                                    <th className="p-3 bg-yellow-500/10 text-yellow-750">90-Day Target</th>
                                    <th className="p-3 text-right">Numeric Change</th>
                                    <th className="p-3 text-right">% Change</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                  {d3.range(3).map(idx => {
                                    const dr = data.drivers[idx];
                                    const calc = template.calculate(data.drivers, data.trajectory).drivers[idx];
                                    return (
                                      <tr key={idx} className="hover:bg-slate-50/50">
                                        <td className="p-3 font-semibold text-slate-700">{dr.driver}</td>
                                        <td className="p-3 bg-yellow-500/5">
                                          <input
                                            type="number"
                                            value={dr.current}
                                            onChange={e => handleDriverChange(activeTab, idx, "current", e.target.value)}
                                            className="w-24 bg-white border border-slate-200 rounded p-1 text-center font-bold text-yellow-600 focus:outline-none focus:border-pink-500"
                                          />
                                        </td>
                                        <td className="p-3 bg-yellow-500/5">
                                          <input
                                            type="number"
                                            value={dr.target}
                                            onChange={e => handleDriverChange(activeTab, idx, "target", e.target.value)}
                                            className="w-24 bg-white border border-slate-200 rounded p-1 text-center font-bold text-yellow-600 focus:outline-none focus:border-pink-500"
                                          />
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold text-slate-700">
                                          {calc.change > 0 ? `+${calc.change}` : calc.change}
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold text-slate-700">
                                          {(calc.pct * 100).toFixed(1)}%
                                        </td>
                                      </tr>
                                    );
                                  })}
                                  {/* Result Summary row */}
                                  {(() => {
                                    const summary = template.calculate(data.drivers, data.trajectory).summary;
                                    return (
                                      <tr className="bg-slate-50/80 border-t border-slate-200 font-extrabold text-slate-800">
                                        <td className="p-3">
                                          NORTH STAR (monthly on-time orders, mn)
                                        </td>
                                        <td className="p-3 text-center font-mono">
                                          {summary.current.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-center font-mono">
                                          {summary.target.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-right font-mono text-indigo-650">
                                          +{summary.change.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-right font-mono text-emerald-600">
                                          +{(summary.pct * 100).toFixed(1)}%
                                        </td>
                                      </tr>
                                    );
                                  })()}
                                </tbody>
                              </table>
                            </div>
                          </div>

                          {/* Part 2: Monthly Trajectory */}
                          <div>
                            <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider mb-2">
                              2. Monthly Trajectory Actual vs Target
                            </h4>
                            <div className="overflow-x-auto max-h-60 border border-slate-200 rounded-lg">
                              <table className="w-full text-xs text-left border-collapse">
                                <thead className="bg-slate-50 text-slate-655 font-bold border-b border-slate-200">
                                  <tr>
                                    <th className="p-2.5 pl-4">Month</th>
                                    <th className="p-2.5 bg-yellow-500/10 text-yellow-750">NSM Actual (mn)</th>
                                    <th className="p-2.5 bg-yellow-500/10 text-yellow-750">NSM Target (mn)</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                  {data.trajectory.map((t, idx) => (
                                    <tr key={idx} className="hover:bg-slate-50/30">
                                      <td className="p-2 pl-4 font-bold text-slate-500">{t.month}</td>
                                      <td className="p-2 bg-yellow-500/5">
                                        <input
                                          type="number"
                                          placeholder="—"
                                          value={t.actual === null ? '' : t.actual}
                                          onChange={e => handleTrajectoryChange(activeTab, idx, "actual", e.target.value)}
                                          className="w-28 bg-white border border-slate-200 rounded p-1 text-center font-bold text-yellow-600"
                                        />
                                      </td>
                                      <td className="p-2 bg-yellow-500/5">
                                        <input
                                          type="number"
                                          placeholder="—"
                                          value={t.target === null ? '' : t.target}
                                          onChange={e => handleTrajectoryChange(activeTab, idx, "target", e.target.value)}
                                          className="w-28 bg-white border border-slate-200 rounded p-1 text-center font-bold text-yellow-600"
                                        />
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </div>
                      );
                    }

                    if (activeTab === "UnitEconomics") {
                      const res = template.calculate(data.inputs);
                      return (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                          {/* Inputs Panel */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider">
                              Calculator Inputs
                            </h4>
                            <div className="space-y-3 bg-slate-50 p-4 rounded-xl border border-slate-200">
                              <div>
                                <label className="block text-xs text-slate-500 mb-1">
                                  ARPU (Average Revenue Per User / month)
                                </label>
                                <input
                                  type="number"
                                  value={data.inputs.arpu}
                                  onChange={e => handleInputChange(activeTab, "arpu", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-2 text-yellow-600 font-bold text-sm focus:outline-none focus:border-pink-500"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-500 mb-1">
                                  Gross Margin (%, e.g. 30 for 30%)
                                </label>
                                <input
                                  type="number"
                                  min="0"
                                  max="100"
                                  step="1"
                                  value={data.inputs.margin}
                                  onChange={e => handleInputChange(activeTab, "margin", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-2 text-yellow-600 font-bold text-sm focus:outline-none focus:border-pink-500"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-500 mb-1">
                                  Monthly Churn Rate (%, e.g. 5 for 5%)
                                </label>
                                <input
                                  type="number"
                                  min="0"
                                  max="100"
                                  step="1"
                                  value={data.inputs.churn}
                                  onChange={e => handleInputChange(activeTab, "churn", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-2 text-yellow-600 font-bold text-sm focus:outline-none focus:border-pink-500"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-500 mb-1">
                                  CAC (Customer Acquisition Cost)
                                </label>
                                <input
                                  type="number"
                                  value={data.inputs.cac}
                                  onChange={e => handleInputChange(activeTab, "cac", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-2 text-yellow-600 font-bold text-sm focus:outline-none focus:border-pink-500"
                                />
                              </div>
                            </div>
                          </div>

                          {/* Calculations & Results */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider">
                              Results & Sensitivity Analysis
                            </h4>
                            
                            <div className="grid grid-cols-2 gap-3">
                              <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-center">
                                <span className="text-[10px] text-slate-400 font-bold uppercase">Customer Lifetime</span>
                                <div className="text-lg font-black text-slate-800 mt-1">{res.lifetime} mo</div>
                              </div>
                              <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-center">
                                <span className="text-[10px] text-slate-400 font-bold uppercase">LTV (Lifetime Value)</span>
                                <div className="text-lg font-black text-slate-800 mt-1">₹{res.ltv.toFixed(1)}</div>
                              </div>
                              <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-center">
                                <span className="text-[10px] text-slate-400 font-bold uppercase">CAC Payback</span>
                                <div className="text-lg font-black text-slate-800 mt-1">{res.payback} mo</div>
                              </div>
                              <div className="bg-slate-50 p-3 rounded-lg border border-slate-200 text-center">
                                <span className="text-[10px] text-slate-400 font-bold uppercase">LTV:CAC Ratio</span>
                                <div className={`text-lg font-black mt-1 ${
                                  res.ratio >= 3 ? 'text-emerald-600' :
                                  res.ratio >= 1 ? 'text-amber-500' : 'text-rose-600'
                                }`}>
                                  {res.ratio}x
                                  <span className="text-[9px] block text-slate-500 font-bold">{res.verdict}</span>
                                </div>
                              </div>
                            </div>

                            <div className="bg-slate-50 p-3.5 rounded-lg border border-slate-200">
                              <span className="text-[10px] font-extrabold uppercase text-slate-500 block mb-2 text-left">
                                Churn Sensitivity Heatmap
                              </span>
                              <table className="w-full text-center text-xs">
                                <thead>
                                  <tr className="text-slate-400 border-b border-slate-200">
                                    <th className="pb-1 text-left">Monthly Churn</th>
                                    <th className="pb-1">Implied LTV</th>
                                    <th className="pb-1">LTV:CAC Ratio</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                  {res.sensitivity.map((sens, sIdx) => (
                                    <tr key={sIdx} className="hover:bg-slate-100/30">
                                      <td className="py-2 text-left font-bold text-slate-600">{(sens.churn * 100).toFixed(0)}%</td>
                                      <td className="py-2 font-mono text-slate-700">₹{sens.ltv}</td>
                                      <td className={`py-2 font-mono font-bold ${
                                        sens.ratio >= 3 ? 'text-emerald-600' :
                                        sens.ratio >= 1.5 ? 'text-blue-650' : 'text-rose-600'
                                      }`}>
                                        {sens.ratio}x
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        </div>
                      );
                    }

                    if (activeTab === "ABTest") {
                      const res = template.calculate(data.inputs);
                      return (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                          {/* Inputs Panel */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-550 tracking-wider">
                              Traffic & Conversions
                            </h4>
                            <div className="grid grid-cols-2 gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
                              <div className="col-span-2 border-b border-slate-200 pb-1.5">
                                <span className="text-[10px] font-bold uppercase text-slate-500">Control (A)</span>
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-500 mb-1">Visitors</label>
                                <input
                                  type="number"
                                  value={data.inputs.controlVisitors}
                                  onChange={e => handleInputChange(activeTab, "controlVisitors", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-1.5 text-yellow-600 font-bold"
                                />
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-500 mb-1">Conversions</label>
                                <input
                                  type="number"
                                  value={data.inputs.controlConversions}
                                  onChange={e => handleInputChange(activeTab, "controlConversions", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-1.5 text-yellow-600 font-bold"
                                />
                              </div>

                              <div className="col-span-2 border-b border-slate-200 pb-1.5 mt-2">
                                <span className="text-[10px] font-bold uppercase text-indigo-650">Variant (B)</span>
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-500 mb-1">Visitors</label>
                                <input
                                  type="number"
                                  value={data.inputs.variantVisitors}
                                  onChange={e => handleInputChange(activeTab, "variantVisitors", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-1.5 text-yellow-600 font-bold"
                                />
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-500 mb-1">Conversions</label>
                                <input
                                  type="number"
                                  value={data.inputs.variantConversions}
                                  onChange={e => handleInputChange(activeTab, "variantConversions", e.target.value)}
                                  className="w-full bg-white border border-slate-200 rounded p-1.5 text-yellow-600 font-bold"
                                />
                              </div>
                            </div>
                          </div>

                          {/* Calculations & Results */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider">
                              Significance Results
                            </h4>
                            
                            <div className="space-y-3 bg-slate-50 p-4 rounded-xl border border-slate-200">
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-500">Control Conv. Rate:</span>
                                <span className="font-mono font-bold text-slate-800">{(res.cr * 100).toFixed(2)}%</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-500">Variant Conv. Rate:</span>
                                <span className="font-mono font-bold text-slate-800">{(res.vr * 100).toFixed(2)}%</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-500">Relative Uplift:</span>
                                <span className={`font-mono font-extrabold ${res.uplift >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                                  {res.uplift >= 0 ? `+${(res.uplift * 100).toFixed(1)}%` : `${(res.uplift * 100).toFixed(1)}%`}
                                </span>
                              </div>
                              <div className="flex justify-between text-xs border-t border-slate-200 pt-2">
                                <span className="text-slate-500">Z-Score:</span>
                                <span className="font-mono font-extrabold text-indigo-600">{res.zScore}</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-500">Significant at 95%? (|z|&gt;1.96):</span>
                                <span className={`font-bold ${res.sig95 === "YES ✓" ? "text-emerald-600" : "text-slate-400"}`}>{res.sig95}</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-500">Significant at 99%? (|z|&gt;2.576):</span>
                                <span className={`font-bold ${res.sig99 === "YES ✓" ? "text-emerald-600" : "text-slate-400"}`}>{res.sig99}</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    }

                    const finalRows = template.calculate ? template.calculate(data.rows, data.weights).rows || template.calculate(data.rows, data.weights) : data.rows;
                    
                    let processedData = finalRows;
                    let moscowStats = null;
                    if (activeTab === "MoSCoW" && template.calculate) {
                      const stats = template.calculate(data.rows);
                      processedData = stats.rows;
                      moscowStats = stats;
                    }
                    if (activeTab === "CohortRetention" && template.calculate) {
                      const stats = template.calculate(data.rows);
                      processedData = stats.rows;
                    }
                    if (activeTab === "PMFSurvey" && template.calculate) {
                      const stats = template.calculate(data.rows);
                      processedData = stats.rows;
                    }

                    return (
                      <div className="space-y-4">
                        {/* Special case: Weighted Scoring weight headers */}
                        {activeTab === "WeightedScoring" && (
                          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex items-center gap-4 flex-wrap text-xs shadow-inner">
                            <span className="font-bold text-slate-550">Adjust Criteria Weights (Sum must equal 100%):</span>
                            {Object.keys(data.weights).map(wKey => {
                              const labels = { fit: "Strategic Fit", revenue: "Revenue Upside", speed: "Speed to Ship", risk: "Low Risk" };
                              return (
                                <div key={wKey} className="flex items-center gap-1.5">
                                  <span className="text-slate-500 font-bold">{labels[wKey]}:</span>
                                  <input
                                    type="number"
                                    min="0"
                                    max="100"
                                    step="1"
                                    value={data.weights[wKey]}
                                    onChange={e => {
                                      let raw = e.target.value;
                                      if (typeof raw === 'string' && raw.length > 1 && raw.startsWith('0') && raw[1] !== '.') {
                                        raw = raw.replace(/^0+/, '');
                                      }
                                      handleWeightChange(activeTab, wKey, raw);
                                    }}
                                    className="w-14 bg-white border border-slate-200 rounded p-1 text-center font-bold text-yellow-600 focus:outline-none focus:border-pink-500"
                                  />
                                </div>
                              );
                            })}
                            {(() => {
                              const sum = Object.values(data.weights).reduce((a, b) => a + b, 0);
                              const isOk = Math.abs(sum - 100) < 0.5;
                              return (
                                <span className={`font-bold ml-auto px-2 py-0.5 rounded text-[10px] ${isOk ? 'bg-emerald-50 text-emerald-600 border border-emerald-200' : 'bg-rose-50 text-rose-600 border border-rose-200'}`}>
                                  {isOk ? "Sum: 100% ✓" : `Sum: ${sum.toFixed(0)}% (FIX)`}
                                </span>
                              );
                            })()}
                          </div>
                        )}

                        {/* RENDER TABLE CONTAINER */}
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs text-left border border-slate-200 rounded-lg overflow-hidden">
                            <thead className="bg-slate-50 text-slate-655 font-bold border-b border-slate-200">
                              <tr>
                                {template.headers.map((h, hIdx) => (
                                  <th 
                                    key={hIdx} 
                                    className={`p-3 ${h.editable ? 'bg-yellow-500/10 text-yellow-750' : 'text-slate-600'} ${
                                      h.type === 'number' || h.type === 'percent' ? 'text-center' : ''
                                    }`}
                                  >
                                    {h.label}
                                  </th>
                                ))}
                                <th className="p-3 w-10 text-center"></th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                              {processedData.map((row, rIdx) => (
                                <tr key={row.id || rIdx} className="hover:bg-slate-50/40">
                                  {template.headers.map((h, hIdx) => {
                                    const val = row[h.key];
                                    
                                    if (h.editable) {
                                      return (
                                        <td key={hIdx} className="p-2 bg-yellow-500/5">
                                          {h.type === "select" ? (
                                            <select
                                              value={val}
                                              onChange={e => {
                                                const v = h.valueType === "number" ? Number(e.target.value) : e.target.value;
                                                handleCellChange(activeTab, rIdx, h.key, v);
                                              }}
                                              className="w-full bg-white border border-slate-200 rounded p-1 text-slate-700 font-bold focus:outline-none focus:border-pink-500"
                                            >
                                              {h.options.map((opt, oIdx) => {
                                                const isObj = typeof opt === "object";
                                                const oVal = isObj ? opt.value : opt;
                                                const oLbl = isObj ? opt.label : opt;
                                                return <option key={oIdx} value={oVal}>{oLbl}</option>;
                                              })}
                                            </select>
                                          ) : (
                                            <input
                                              type={h.type === "number" || h.type === "percent" ? "number" : "text"}
                                              min={h.min}
                                              max={h.max}
                                              step={h.step || (h.type === "percent" ? "0.05" : "1")}
                                              value={val}
                                              onChange={e => {
                                                let raw = e.target.value;
                                                if (typeof raw === 'string' && raw.length > 1 && raw.startsWith('0') && raw[1] !== '.') {
                                                  raw = raw.replace(/^0+/, '');
                                                }
                                                let v = raw;
                                                if (h.type === "number" || h.type === "percent") {
                                                  if (raw !== '') {
                                                    v = Number(raw);
                                                    if (h.min !== undefined && v < h.min) v = h.min;
                                                    if (h.max !== undefined && v > h.max) v = h.max;
                                                  } else {
                                                    v = 0;
                                                  }
                                                }
                                                handleCellChange(activeTab, rIdx, h.key, v);
                                              }}
                                              className={`w-full bg-white border border-slate-200 rounded p-1 font-bold text-yellow-600 focus:outline-none focus:border-pink-500 ${
                                                h.type === "number" || h.type === "percent" ? "text-center" : "text-left"
                                              }`}
                                            />
                                          )}
                                        </td>
                                      );
                                    } else {
                                      let displayVal = val;
                                      if (h.type === "percent") {
                                        displayVal = (val * 100).toFixed(0) + "%";
                                      } else if (typeof val === "number" && !Number.isInteger(val)) {
                                        displayVal = val.toFixed(2);
                                      }
                                      return (
                                        <td 
                                          key={hIdx} 
                                          className={`p-3 font-mono font-bold text-slate-700 ${
                                            h.type === 'number' || h.type === 'percent' ? 'text-center' : ''
                                          }`}
                                        >
                                          {displayVal}
                                        </td>
                                      );
                                    }
                                  })}
                                  <td className="p-2 w-10 text-center">
                                    {row.force ? null : (
                                      <button
                                        onClick={() => handleDeleteRow(activeTab, rIdx)}
                                        className="text-slate-400 hover:text-rose-600 p-1 rounded hover:bg-rose-50 transition-colors"
                                      >
                                        <Trash2 size={13} />
                                      </button>
                                    )}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>

                        {/* Special case outputs for MoSCoW, PMF Survey, etc. */}
                        {moscowStats && (
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs">
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Must-have Effort:</span>
                              <span className="text-sm font-extrabold text-slate-800">{moscowStats.summary.M.effort} days ({moscowStats.summary.M.count} items)</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Should-have Effort:</span>
                              <span className="text-sm font-extrabold text-slate-800">{moscowStats.summary.S.effort} days ({moscowStats.summary.S.count} items)</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Total Effort:</span>
                              <span className="text-sm font-extrabold text-slate-800">{moscowStats.totalEffort} days</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Must-have % of Effort:</span>
                              <span className={`text-sm font-extrabold ${moscowStats.mustPercent <= 60 ? 'text-emerald-600' : 'text-rose-600'}`}>
                                {moscowStats.mustPercent.toFixed(1)}% 
                                {moscowStats.mustPercent <= 60 ? ' (Healthy ✓)' : ' (Too high - risk!)'}
                              </span>
                            </div>
                          </div>
                        )}

                        {activeTab === "CohortRetention" && (() => {
                          const retentionStats = template.calculate(data.rows);
                          return (
                            <div>
                              <h4 className="text-xs font-extrabold uppercase text-slate-500 tracking-wider mb-2">
                                2. Cohort Retention Heatmap (%)
                              </h4>
                              <div className="overflow-x-auto">
                                <table className="w-full text-xs text-center border border-slate-200 rounded-lg overflow-hidden">
                                  <thead className="bg-slate-50 text-slate-655 font-bold border-b border-slate-200">
                                    <tr>
                                      <th className="p-2.5 text-left pl-4">Cohort</th>
                                      <th className="p-2.5">Size (M0)</th>
                                      <th className="p-2.5">M1</th>
                                      <th className="p-2.5">M2</th>
                                      <th className="p-2.5">M3</th>
                                      <th className="p-2.5">M4</th>
                                      <th className="p-2.5">M5</th>
                                      <th className="p-2.5">M6</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-slate-100">
                                    {retentionStats.calculated.map((cRow, cIdx) => (
                                      <tr key={cIdx} className="hover:bg-slate-50/40">
                                        <td className="p-2 pl-4 text-left font-bold text-slate-500">{cRow.cohort}</td>
                                        <td className="p-2 font-semibold text-slate-700">{cRow.size}</td>
                                        {d3.range(7).map(mNum => {
                                          const key = `m${mNum}`;
                                          const val = cRow[key];
                                          // Heatmap coloring based on percentage
                                          const green = Math.round(val * 160 + 95);
                                          return (
                                            <td 
                                              key={mNum} 
                                              style={{ backgroundColor: `rgba(99, 102, 241, ${val * 0.3})` }}
                                              className="p-2.5 font-bold text-slate-800 border border-slate-200/50"
                                            >
                                              {mNum === 0 ? "100%" : (val * 100).toFixed(0) + "%"}
                                            </td>
                                          );
                                        })}
                                      </tr>
                                    ))}
                                    {/* Average Cohort Row */}
                                    <tr className="bg-slate-50 font-extrabold border-t border-slate-200 text-slate-800">
                                      <td className="p-2 pl-4 text-left text-slate-600">Average</td>
                                      <td className="p-2 text-slate-400">—</td>
                                      <td className="p-2 text-indigo-600">{(retentionStats.averages.m0 * 100)}%</td>
                                      <td className="p-2 text-indigo-600">{(retentionStats.averages.m1 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-600">{(retentionStats.averages.m2 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-600">{(retentionStats.averages.m3 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-600">{(retentionStats.averages.m4 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-650">{(retentionStats.averages.m5 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-650">{(retentionStats.averages.m6 * 100).toFixed(0)}%</td>
                                    </tr>
                                  </tbody>
                                </table>
                              </div>
                            </div>
                          );
                        })()}
                      </div>
                    );
                  })()}
                </div>

                {/* 3. DYNAMIC INTERACTIVE VISUALIZATIONS */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  
                  {/* Left Column: Visual Chart representation */}
                  {(() => {
                    if (activeTab === "RICE") return <RiceBarChart data={PM_TEMPLATES.RICE.calculate(frameworkData.RICE.rows)} setTooltip={setTooltip} />;
                    if (activeTab === "WeightedScoring") return <WeightedBarChart data={PM_TEMPLATES.WeightedScoring.calculate(frameworkData.WeightedScoring.rows, frameworkData.WeightedScoring.weights)} setTooltip={setTooltip} />;
                    if (activeTab === "ValueVsEffort") return <ValueVsEffortChart data={frameworkData.ValueVsEffort.rows} onUpdateRow={(id, fields) => handleRowDataUpdate("ValueVsEffort", id, fields)} setTooltip={setTooltip} />;
                    if (activeTab === "KanoModel") return <KanoScatterPlot data={PM_TEMPLATES.KanoModel.calculate(frameworkData.KanoModel.rows)} setTooltip={setTooltip} />;
                    if (activeTab === "WSJF") {
                      const finalWsjf = PM_TEMPLATES.WSJF.calculate(frameworkData.WSJF.rows);
                      return <RiceBarChart data={finalWsjf.map(d => ({ name: d.name, score: d.wsjf, rank: d.rank }))} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "MoSCoW") {
                      const stats = PM_TEMPLATES.MoSCoW.calculate(frameworkData.MoSCoW.rows);
                      return <MoscowDonutChart data={stats} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "OpportunityScoring") {
                      const finalOpp = PM_TEMPLATES.OpportunityScoring.calculate(frameworkData.OpportunityScoring.rows);
                      return <WeightedBarChart data={finalOpp.map(d => ({ name: d.name, score: d.opportunity }))} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "BCGMatrix") {
                      return <BCGBubbleChart data={PM_TEMPLATES.BCGMatrix.calculate(frameworkData.BCGMatrix.rows)} onUpdateRow={(id, fields) => handleRowDataUpdate("BCGMatrix", id, fields)} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "AnsoffMatrix") {
                      const finalAnsoff = PM_TEMPLATES.AnsoffMatrix.calculate(frameworkData.AnsoffMatrix.rows);
                      return <WeightedBarChart data={finalAnsoff.map(d => ({ name: d.name, score: d.riskAdjusted }))} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "Porter5Forces") {
                      return <PorterForcesChart data={frameworkData.Porter5Forces.rows} onUpdateRow={handlePorterRowUpdate} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "NorthStar") {
                      return <NorthStarLineChart trajectory={frameworkData.NorthStar.trajectory} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "AARRR") {
                      const finalAarrr = PM_TEMPLATES.AARRR.calculate(frameworkData.AARRR.rows);
                      return <AARRRFunnelChart data={finalAarrr} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "HEART") {
                      const finalHeart = PM_TEMPLATES.HEART.calculate(frameworkData.HEART.rows);
                      return <WeightedBarChart data={finalHeart.map(d => ({ name: d.category, score: d.attainment * 100 }))} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "UnitEconomics") {
                      const ueStats = PM_TEMPLATES.UnitEconomics.calculate(frameworkData.UnitEconomics.inputs);
                      return <WeightedBarChart data={ueStats.sensitivity.map(s => ({ name: `${(s.churn * 100).toFixed(0)}% Churn`, score: s.ratio }))} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "CohortRetention") {
                      const finalCohort = PM_TEMPLATES.CohortRetention.calculate(frameworkData.CohortRetention.rows);
                      return <CohortRetentionChart data={finalCohort} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "PMFSurvey") {
                      const finalPmf = PM_TEMPLATES.PMFSurvey.calculate(frameworkData.PMFSurvey.rows);
                      return <ABTestChart data={{
                        cr: finalPmf.score, vr: 0.40, inputs: { controlVisitors: finalPmf.valid, controlConversions: 0, variantVisitors: 100, variantConversions: 40 }
                      }} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "ABTest") {
                      const abStats = PM_TEMPLATES.ABTest.calculate(frameworkData.ABTest.inputs);
                      return <ABTestChart data={abStats} setTooltip={setTooltip} />;
                    }
                    if (activeTab === "OKRTracker") {
                      const finalOkrs = PM_TEMPLATES.OKRTracker.calculate(frameworkData.OKRTracker.rows);
                      return <RiceBarChart data={finalOkrs.map(d => ({ name: d.kr, score: d.progress * 100 }))} setTooltip={setTooltip} />;
                    }
                    
                    return null;
                  })()}

                  {/* Right Column: Key takeaways or analysis */}
                  <div className="bg-white border border-slate-200 p-5 rounded-xl flex flex-col justify-between shadow-sm">
                    <div>
                      <div className="flex items-center gap-2 mb-3 text-pink-600">
                        <TrendingUp size={16} />
                        <h4 className="text-xs font-bold uppercase tracking-wider">Playbook Readout & Verdict</h4>
                      </div>
                      <p className="text-xs text-slate-700 leading-relaxed font-bold mb-4 bg-slate-50 p-3 rounded-lg border-l-4 border-pink-500">
                        {(() => {
                          if (activeTab === "RICE") return "Build top-ranked items first. If two RICE scores are close, prioritize the one with lower Effort as it de-risks value faster. Beware of inflated reach estimates — anchor them to actual customer metrics.";
                          if (activeTab === "WeightedScoring") return "Agree on criteria weights with all major stakeholders BEFORE grading options. This avoids reverse-engineering weights to support a favorite option.";
                          if (activeTab === "ValueVsEffort") return "Quick Wins build early momentum and stakeholder trust. Big Bets require strategic alignment. Money Pits (low value, high effort) should be declined. Fill-ins are strictly for spare capacity.";
                          if (activeTab === "KanoModel") return "A healthy roadmap requires satisfying Must-bes (basic core trust) and investing in key Performance areas. Sprinkle in 1-2 Delighters to stand out. Indifferents should be ignored.";
                          if (activeTab === "WSJF") return "Weighted Shortest Job First favors smaller, high-value jobs to optimize value delivery throughput. If all tasks score high in Time Criticality, force stakeholders to rank them relatively.";
                          if (activeTab === "MoSCoW") return "If Must-have effort exceeds ~60% of capacity, you run a high risk of project delays. Cut scope early rather than waiting for launch-date pressure.";
                          if (activeTab === "OpportunityScoring") return "Prioritize outcomes with high opportunity scores (importance is high, satisfaction is low). Outcomes with high satisfaction are table-stakes; over-serving them is wasteful.";
                          if (activeTab === "BCGMatrix") return "Fund Stars to secure market leadership, milk Cash Cows to generate the cash required, and back only 1-2 promising Question Marks. Divest Dogs to free up resources.";
                          if (activeTab === "AnsoffMatrix") return "Existing products in existing markets (Penetration) are the safest bet. Diversification (new product, new market) is a high-risk moonshot. Balance your roadmap accordingly.";
                          if (activeTab === "Porter5Forces") return "Assess structural profitability before entering. A high-threat market requires a highly differentiated entry strategy (a wedge) and a defensible moat.";
                          if (activeTab === "NorthStar") return "Your North Star Metric is a leading indicator of long-term revenue. If input drivers show green but the NSM remains flat, your input value model requires revision.";
                          if (activeTab === "AARRR") return "Do not waste spend driving Acquisition if your Activation stage is leaking. Prioritize optimizing retention first; it makes every top-funnel click cheaper.";
                          if (activeTab === "HEART") return "Pair user experience metrics with a Goal-Signal-Metric chain to ensure you measure actual behavior rather than ease of data collection. Task success is the hardest to fake.";
                          if (activeTab === "UnitEconomics") return "Churn is the silent killer: halving churn doubles LTV. If LTV:CAC is below 3, focus on lowering acquisition costs or improving customer retention before scaling spend.";
                          if (activeTab === "CohortRetention") return "A retention curve that flattens above zero indicates product-market fit (PMF). Newer cohorts flattening at higher levels confirms that product modifications are working.";
                          if (activeTab === "PMFSurvey") return "If the score is below 40%, segment the 'very disappointed' cohort to identify who they are and what they value. Build features explicitly for them rather than trying to satisfy all users.";
                          if (activeTab === "ABTest") return "Ensure sample sizes are calculated and locked in advance to prevent early peeking. Statistical significance is important, but practical significance (impact vs cost) is crucial.";
                          if (activeTab === "OKRTracker") return "OKRs track outcomes (e.g. increase conversion to 92%), not activities (e.g. build feature X). Grade KRs regularly and decouple stretch goals from performance reviews.";
                          return "Analyze metrics and adjust product strategies accordingly.";
                        })()}
                      </p>
                      
                      <div className="space-y-2 mt-4 text-[11px] text-slate-500 leading-relaxed bg-slate-50 p-3 rounded border border-slate-200">
                        <span className="font-bold text-slate-700 block mb-1">Methodology Checklist:</span>
                        <div className="flex items-start gap-1.5 text-slate-600">
                          <CheckCircle2 size={13} className="text-pink-600 mt-0.5 shrink-0" />
                          <span>Drag interactive elements on the D3 charts to dynamically modify table inputs.</span>
                        </div>
                        <div className="flex items-start gap-1.5 text-slate-600">
                          <CheckCircle2 size={13} className="text-pink-600 mt-0.5 shrink-0" />
                          <span>Calculations and rankings update in real-time.</span>
                        </div>
                        <div className="flex items-start gap-1.5 text-slate-600">
                          <CheckCircle2 size={13} className="text-pink-600 mt-0.5 shrink-0" />
                          <span>Local storage persistence maintains your edits between browser sessions.</span>
                        </div>
                      </div>
                    </div>
                  </div>

                </div>

              </div>
            );
          })()}

        </div>
      </section>

      {/* FLOATING RICH D3 TOOLTIP CONTAINER */}
      <Tooltip tooltip={tooltip} />

    </div>
  );
};

export default AdminPMPlayground;
