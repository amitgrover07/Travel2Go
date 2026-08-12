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
  PieChart, 
  Users, 
  Info, 
  Search, 
  CheckCircle2, 
  AlertTriangle,
  HelpCircle,
  Activity
} from 'lucide-react';
import toast from 'react-hot-toast';
import { PM_TEMPLATES } from '../utils/pmTemplates';

// --- GOOGLE CHARTS DYNAMIC LOADER ---
let googleChartsLoading = false;
let googleChartsLoaded = false;
const loadGoogleCharts = (callback) => {
  if (window.google && window.google.charts) {
    if (!googleChartsLoaded) {
      window.google.charts.load('current', { packages: ['corechart', 'radar'] });
      window.google.charts.setOnLoadCallback(() => {
        googleChartsLoaded = true;
        callback();
      });
    } else {
      callback();
    }
    return;
  }
  if (googleChartsLoading) {
    const checkInterval = setInterval(() => {
      if (window.google && window.google.charts && googleChartsLoaded) {
        clearInterval(checkInterval);
        callback();
      }
    }, 100);
    return;
  }
  googleChartsLoading = true;
  const script = document.createElement('script');
  script.src = 'https://www.gstatic.com/charts/loader.js';
  script.async = true;
  script.onload = () => {
    window.google.charts.load('current', { packages: ['corechart'] });
    window.google.charts.setOnLoadCallback(() => {
      googleChartsLoaded = true;
      googleChartsLoading = false;
      callback();
    });
  };
  document.head.appendChild(script);
};

// ============================================================================
// --- CUSTOM D3 & SVG CHART COMPONENTS ---
// ============================================================================

// 1. RICE Bar Chart
const RiceBarChart = ({ data }) => {
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

    // Sort descending
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
      .attr("color", "#94a3b8");

    g.append("g")
      .call(d3.axisLeft(yScale))
      .attr("color", "#94a3b8")
      .selectAll("text")
      .attr("font-size", "10px")
      .attr("font-weight", "500")
      .attr("fill", "#94a3b8");

    // Drawing gradient
    const grad = svg.append("defs")
      .append("linearGradient")
      .attr("id", "riceGrad")
      .attr("x1", "0%").attr("y1", "0%")
      .attr("x2", "100%").attr("y2", "0%");
    grad.append("stop").attr("offset", "0%").attr("stop-color", "#6366f1");
    grad.append("stop").attr("offset", "100%").attr("stop-color", "#4f46e5");

    g.selectAll(".bar")
      .data(sortedData)
      .enter()
      .append("rect")
      .attr("class", "bar")
      .attr("y", d => yScale(d.name))
      .attr("x", 0)
      .attr("height", yScale.bandwidth())
      .attr("width", d => xScale(d.score))
      .attr("fill", "url(#riceGrad)")
      .attr("rx", 4);

    g.selectAll(".label")
      .data(sortedData)
      .enter()
      .append("text")
      .attr("class", "label")
      .attr("y", d => yScale(d.name) + yScale.bandwidth() / 2 + 4)
      .attr("x", d => Math.min(xScale(d.score) + 5, chartWidth - 35))
      .attr("fill", d => xScale(d.score) > chartWidth - 50 ? "#fff" : "#f1f5f9")
      .attr("font-size", "9px")
      .attr("font-weight", "bold")
      .text(d => Math.round(d.score));

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Features Ranked by RICE Score</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 2. Weighted Scoring Bar Chart
const WeightedBarChart = ({ data }) => {
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
      .attr("color", "#94a3b8");

    g.append("g")
      .call(d3.axisLeft(yScale))
      .attr("color", "#94a3b8")
      .selectAll("text")
      .attr("font-size", "10px")
      .attr("fill", "#94a3b8");

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
      .attr("y", d => yScale(d.name))
      .attr("x", 0)
      .attr("height", yScale.bandwidth())
      .attr("width", d => xScale(d.score || 0))
      .attr("fill", "url(#weightedGrad)")
      .attr("rx", 4);

    g.selectAll(".label")
      .data(sortedData)
      .enter()
      .append("text")
      .attr("y", d => yScale(d.name) + yScale.bandwidth() / 2 + 4)
      .attr("x", d => xScale(d.score || 0) + 5)
      .attr("fill", "#f1f5f9")
      .attr("font-size", "10px")
      .attr("font-weight", "bold")
      .text(d => d.score?.toFixed(2));

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Weighted Total Scores</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 3. Value vs Effort (2x2 Scatter Plot)
const ValueVsEffortChart = ({ data }) => {
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

    const xScale = d3.scaleLinear().domain([0, 10]).range([0, chartWidth]);
    const yScale = d3.scaleLinear().domain([0, 10]).range([chartHeight, 0]);

    // Draw quadrant fills
    // Top-Left (Quick Wins): Value >= 5, Effort < 5
    g.append("rect")
      .attr("x", xScale(0)).attr("y", yScale(10))
      .attr("width", xScale(5) - xScale(0))
      .attr("height", yScale(5) - yScale(10))
      .attr("fill", "#22c55e").attr("opacity", 0.08);

    // Top-Right (Big Bets): Value >= 5, Effort >= 5
    g.append("rect")
      .attr("x", xScale(5)).attr("y", yScale(10))
      .attr("width", xScale(10) - xScale(5))
      .attr("height", yScale(5) - yScale(10))
      .attr("fill", "#3b82f6").attr("opacity", 0.08);

    // Bottom-Left (Fill-ins): Value < 5, Effort < 5
    g.append("rect")
      .attr("x", xScale(0)).attr("y", yScale(5))
      .attr("width", xScale(5) - xScale(0))
      .attr("height", yScale(0) - yScale(5))
      .attr("fill", "#64748b").attr("opacity", 0.08);

    // Bottom-Right (Money Pits): Value < 5, Effort >= 5
    g.append("rect")
      .attr("x", xScale(5)).attr("y", yScale(5))
      .attr("width", xScale(10) - xScale(5))
      .attr("height", yScale(0) - yScale(5))
      .attr("fill", "#ef4444").attr("opacity", 0.08);

    // Divider Lines
    g.append("line")
      .attr("x1", xScale(5)).attr("y1", yScale(0))
      .attr("x2", xScale(5)).attr("y2", yScale(10))
      .attr("stroke", "#475569").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    g.append("line")
      .attr("x1", xScale(0)).attr("y1", yScale(5))
      .attr("x2", xScale(10)).attr("y2", yScale(5))
      .attr("stroke", "#475569").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    // Grid labels
    g.append("text").attr("x", xScale(2.5)).attr("y", yScale(9.4)).attr("text-anchor", "middle").attr("fill", "#22c55e").attr("font-size", "11px").attr("font-weight", "bold").text("Quick Wins (Do First)");
    g.append("text").attr("x", xScale(7.5)).attr("y", yScale(9.4)).attr("text-anchor", "middle").attr("fill", "#3b82f6").attr("font-size", "11px").attr("font-weight", "bold").text("Big Bets (Strategic)");
    g.append("text").attr("x", xScale(2.5)).attr("y", yScale(0.6)).attr("text-anchor", "middle").attr("fill", "#94a3b8").attr("font-size", "11px").attr("font-weight", "bold").text("Fill-ins (Nice to Have)");
    g.append("text").attr("x", xScale(7.5)).attr("y", yScale(0.6)).attr("text-anchor", "middle").attr("fill", "#f43f5e").attr("font-size", "11px").attr("font-weight", "bold").text("Money Pits (Drop)");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(10))
      .attr("color", "#475569");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(10))
      .attr("color", "#475569");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Effort (1-10) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Value (1-10) →");

    // Plot items
    const dots = g.selectAll(".dot")
      .data(data)
      .enter()
      .append("g");

    dots.append("circle")
      .attr("cx", d => xScale(Number(d.effort) || 0))
      .attr("cy", d => yScale(Number(d.value) || 0))
      .attr("r", 6.5)
      .attr("fill", d => {
        const val = Number(d.value) || 0;
        const eff = Number(d.effort) || 0;
        if (val >= 5 && eff < 5) return "#22c55e";
        if (val >= 5 && eff >= 5) return "#3b82f6";
        if (val < 5 && eff < 5) return "#94a3b8";
        return "#f43f5e";
      })
      .attr("stroke", "#fff")
      .attr("stroke-width", 1.5);

    dots.append("text")
      .attr("x", d => xScale(Number(d.effort) || 0) + 9)
      .attr("y", d => yScale(Number(d.value) || 0) + 4)
      .text(d => d.name)
      .attr("font-size", "9px")
      .attr("font-weight", "600")
      .attr("fill", "#cbd5e1");

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Value vs Effort 2x2 Map</span>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 4. Kano Model Better vs Worse Scatter Plot
const KanoScatterPlot = ({ data }) => {
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

    // Draw quadrant divider lines
    g.append("line")
      .attr("x1", xScale(-0.5)).attr("y1", yScale(0))
      .attr("x2", xScale(-0.5)).attr("y2", yScale(1))
      .attr("stroke", "#475569").attr("stroke-dasharray", "3,3");

    g.append("line")
      .attr("x1", xScale(-1)).attr("y1", yScale(0.5))
      .attr("x2", xScale(0)).attr("y2", yScale(0.5))
      .attr("stroke", "#475569").attr("stroke-dasharray", "3,3");

    // Grid labels
    g.append("text").attr("x", xScale(-0.25)).attr("y", yScale(0.85)).attr("text-anchor", "middle").attr("fill", "#6366f1").attr("font-size", "10px").attr("font-weight", "bold").text("Delighters");
    g.append("text").attr("x", xScale(-0.75)).attr("y", yScale(0.85)).attr("text-anchor", "middle").attr("fill", "#06b6d4").attr("font-size", "10px").attr("font-weight", "bold").text("Performance");
    g.append("text").attr("x", xScale(-0.75)).attr("y", yScale(0.15)).attr("text-anchor", "middle").attr("fill", "#e11d48").attr("font-size", "10px").attr("font-weight", "bold").text("Must-be");
    g.append("text").attr("x", xScale(-0.25)).attr("y", yScale(0.15)).attr("text-anchor", "middle").attr("fill", "#94a3b8").attr("font-size", "10px").attr("font-weight", "bold").text("Indifferent");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5))
      .attr("color", "#475569");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5))
      .attr("color", "#475569");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Worse Coefficient (Dissatisfaction) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Better Coefficient (Satisfaction) →");

    // Plot
    const dots = g.selectAll(".dot")
      .data(data)
      .enter()
      .append("g");

    dots.append("circle")
      .attr("cx", d => xScale(Number(d.worse) || 0))
      .attr("cy", d => yScale(Number(d.better) || 0))
      .attr("r", 6)
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
      .attr("font-weight", "600")
      .attr("fill", "#cbd5e1");

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Kano Better vs Worse Matrix</span>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 5. BCG Matrix (Bubble Chart)
const BCGBubbleChart = ({ data }) => {
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
    const rScale = d3.scaleSqrt().domain([0, d3.max(data, d => d.revenue) || 100]).range([4, 24]);

    // Dividers (Share = 1.0x, Growth = 10%)
    g.append("line")
      .attr("x1", xScale(1.0)).attr("y1", yScale(0))
      .attr("x2", xScale(1.0)).attr("y2", yScale(0.40))
      .attr("stroke", "#475569").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    g.append("line")
      .attr("x1", xScale(0)).attr("y1", yScale(0.10))
      .attr("x2", xScale(2.0)).attr("y2", yScale(0.10))
      .attr("stroke", "#475569").attr("stroke-dasharray", "4,4").attr("stroke-width", 1.5);

    // Quad Labels
    g.append("text").attr("x", xScale(1.5)).attr("y", yScale(0.35)).attr("text-anchor", "middle").attr("fill", "#6366f1").attr("font-size", "11px").attr("font-weight", "bold").text("★ Star");
    g.append("text").attr("x", xScale(0.5)).attr("y", yScale(0.35)).attr("text-anchor", "middle").attr("fill", "#e11d48").attr("font-size", "11px").attr("font-weight", "bold").text("? Question Mark");
    g.append("text").attr("x", xScale(1.5)).attr("y", yScale(0.04)).attr("text-anchor", "middle").attr("fill", "#16a34a").attr("font-size", "11px").attr("font-weight", "bold").text("$ Cash Cow");
    g.append("text").attr("x", xScale(0.5)).attr("y", yScale(0.04)).attr("text-anchor", "middle").attr("fill", "#94a3b8").attr("font-size", "11px").attr("font-weight", "bold").text("Dog");

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale).ticks(5).tickFormat(d => d + "x"))
      .attr("color", "#475569");

    g.append("g")
      .call(d3.axisLeft(yScale).ticks(5).tickFormat(d => (d * 100) + "%"))
      .attr("color", "#475569");

    svg.append("text").attr("x", width / 2).attr("y", height - 5).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Relative Market Share (x) →");
    svg.append("text").attr("transform", "rotate(-90)").attr("x", -height / 2).attr("y", 12).attr("text-anchor", "middle").attr("font-size", "11px").attr("fill", "#94a3b8").text("Market Growth Rate (%) →");

    // Bubbles
    const bubbles = g.selectAll(".bubble")
      .data(data)
      .enter()
      .append("g");

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

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">BCG Portfolio Matrix (Bubble Size = Rev)</span>
      <svg ref={svgRef} width="450" height="320" className="max-w-full" />
    </div>
  );
};

// 6. AARRR Funnel Visual Chart
const AARRRFunnelChart = ({ data }) => {
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

    // Funnel blocks
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

      // Draw block
      g.append("path")
        .attr("d", pathString)
        .attr("fill", d3.interpolatePurples(0.3 + (idx * 0.15)))
        .attr("opacity", 0.95)
        .attr("stroke", "#1e293b")
        .attr("stroke-width", 1.5);

      // Draw label
      g.append("text")
        .attr("x", chartWidth / 2)
        .attr("y", yTop + rowHeight / 2 - 1)
        .attr("text-anchor", "middle")
        .attr("fill", "#ffffff")
        .attr("font-size", "11px")
        .attr("font-weight", "bold")
        .text(`${d.stage}: ${(d.users).toLocaleString()} (${Math.round(d.totalConv * 100)}%)`);

      // Draw conversion steps on the side
      if (idx > 0) {
        g.append("text")
          .attr("x", xTopStart - 10)
          .attr("y", yTop + 3)
          .attr("text-anchor", "end")
          .attr("fill", "#a78bfa")
          .attr("font-size", "10px")
          .attr("font-weight", "bold")
          .text(`↳ ${Math.round(d.stepConv * 100)}%`);
      }
    });

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">AARRR Pirate Funnel Conversion</span>
      <svg ref={svgRef} width="450" height="300" className="max-w-full" />
    </div>
  );
};

// 7. A/B Test Rates Chart
const ABTestChart = ({ data }) => {
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

    // Axes
    g.append("g")
      .attr("transform", `translate(0, ${chartHeight})`)
      .call(d3.axisBottom(xScale))
      .attr("color", "#475569");

    g.append("g")
      .call(d3.axisLeft(yScale).tickFormat(d3.format(".1%")))
      .attr("color", "#475569");

    // Bars
    g.selectAll(".bar")
      .data(chartData)
      .enter()
      .append("rect")
      .attr("x", d => xScale(d.name))
      .attr("y", d => yScale(d.rate))
      .attr("width", xScale.bandwidth())
      .attr("height", d => chartHeight - yScale(d.rate))
      .attr("fill", d => d.name === "Control" ? "#475569" : "#6366f1")
      .attr("rx", 5);

    // Error bars / confidence intervals (95%)
    chartData.forEach(d => {
      const p = d.rate;
      const n = d.visitors;
      if (n <= 0) return;
      const ciHalf = 1.96 * Math.sqrt((p * (1 - p)) / n);
      const lower = Math.max(0, p - ciHalf);
      const upper = p + ciHalf;

      const x = xScale(d.name) + xScale.bandwidth() / 2;

      // Vertical line
      g.append("line")
        .attr("x1", x).attr("y1", yScale(lower))
        .attr("x2", x).attr("y2", yScale(upper))
        .attr("stroke", "#cbd5e1")
        .attr("stroke-width", 1.5);

      // Top cap
      g.append("line")
        .attr("x1", x - 6).attr("y1", yScale(upper))
        .attr("x2", x + 6).attr("y2", yScale(upper))
        .attr("stroke", "#cbd5e1")
        .attr("stroke-width", 1.5);

      // Bottom cap
      g.append("line")
        .attr("x1", x - 6).attr("y1", yScale(lower))
        .attr("x2", x + 6).attr("y2", yScale(lower))
        .attr("stroke", "#cbd5e1")
        .attr("stroke-width", 1.5);
    });

    // Rate Labels
    g.selectAll(".label")
      .data(chartData)
      .enter()
      .append("text")
      .attr("x", d => xScale(d.name) + xScale.bandwidth() / 2)
      .attr("y", d => yScale(d.rate) - 8)
      .attr("text-anchor", "middle")
      .attr("fill", "#fff")
      .attr("font-size", "11px")
      .attr("font-weight", "bold")
      .text(d => (d.rate * 100).toFixed(2) + "%");

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Conversion Rate Comparison (95% CI Error Bars)</span>
      <svg ref={svgRef} width="450" height="280" className="max-w-full" />
    </div>
  );
};

// 8. Custom D3 Radar Chart for Porter's 5 Forces
const PorterRadarChart = ({ data }) => {
  const svgRef = useRef(null);
  useEffect(() => {
    if (!svgRef.current || !data || data.length === 0) return;
    const svg = d3.select(svgRef.current);
    svg.selectAll("*").remove();

    const width = 450;
    const height = 300;
    const margin = { top: 30, right: 60, bottom: 30, left: 60 };

    const chartWidth = width - margin.left - margin.right;
    const chartHeight = height - margin.top - margin.bottom;
    const radius = Math.min(chartWidth, chartHeight) / 2;

    const g = svg.append("g")
      .attr("transform", `translate(${width / 2}, ${height / 2 + 10})`);

    const axisCount = data.length;
    const angleSlice = (Math.PI * 2) / axisCount;

    const rScale = d3.scaleLinear().domain([0, 5]).range([0, radius]);

    // Draw circular grid levels
    const levels = [1, 2, 3, 4, 5];
    levels.forEach(level => {
      const r = rScale(level);
      g.append("circle")
        .attr("cx", 0).attr("cy", 0)
        .attr("r", r)
        .attr("fill", "none")
        .attr("stroke", "#334155")
        .attr("stroke-width", 1);

      g.append("text")
        .attr("x", 4)
        .attr("y", -r + 3)
        .attr("fill", "#64748b")
        .attr("font-size", "8px")
        .text(level);
    });

    // Draw axis lines and labels
    data.forEach((d, idx) => {
      const angle = idx * angleSlice - Math.PI / 2;
      const x = Math.cos(angle) * rScale(5);
      const y = Math.sin(angle) * rScale(5);

      g.append("line")
        .attr("x1", 0).attr("y1", 0)
        .attr("x2", x).attr("y2", y)
        .attr("stroke", "#334155")
        .attr("stroke-width", 1);

      const labelDistance = rScale(5) + 18;
      const lx = Math.cos(angle) * labelDistance;
      const ly = Math.sin(angle) * labelDistance;

      let textAnchor = "middle";
      if (Math.abs(Math.cos(angle)) > 0.1) {
        textAnchor = Math.cos(angle) > 0 ? "start" : "end";
      }

      const forceNameShort = d.force.replace("Supplier power ", "Suppliers ").replace("Buyer power ", "Buyers ").replace("Threat of ", "Threat ");
      g.append("text")
        .attr("x", lx)
        .attr("y", ly + 4)
        .attr("text-anchor", textAnchor)
        .attr("font-size", "9px")
        .attr("font-weight", "600")
        .attr("fill", "#94a3b8")
        .text(forceNameShort);
    });

    // Coordinates for threat polygon
    const points = data.map((d, idx) => {
      const angle = idx * angleSlice - Math.PI / 2;
      const score = Number(d.threat) || 0;
      return [
        Math.cos(angle) * rScale(score),
        Math.sin(angle) * rScale(score)
      ];
    });

    const polygonString = points.map(p => p.join(",")).join(" ");
    
    g.append("polygon")
      .attr("points", polygonString)
      .attr("fill", "#f43f5e")
      .attr("fill-opacity", 0.25)
      .attr("stroke", "#e11d48")
      .attr("stroke-width", 2.5);

    points.forEach((p) => {
      g.append("circle")
        .attr("cx", p[0])
        .attr("cy", p[1])
        .attr("r", 4.5)
        .attr("fill", "#e11d48")
        .attr("stroke", "#fff")
        .attr("stroke-width", 1);
    });

  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Five Forces Threat Profile</span>
      <svg ref={svgRef} width="450" height="300" className="max-w-full" />
    </div>
  );
};


// ============================================================================
// --- GOOGLE CHART WRAPPERS ---
// ============================================================================

// 1. Google Pie Chart (MoSCoW)
const MoscowPieChart = ({ data }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!data) return;
    loadGoogleCharts(() => {
      if (!chartRef.current) return;
      
      const chartData = [
        ['Category', 'Effort (days)'],
        ['Must-have', data.summary.M.effort],
        ['Should-have', data.summary.S.effort],
        ['Could-have', data.summary.C.effort],
        ['Won\'t-have', data.summary.W.effort]
      ];

      const dataTable = window.google.visualization.arrayToDataTable(chartData);

      const options = {
        title: 'Effort Distribution by Priority',
        pieHole: 0.45,
        colors: ['#ef4444', '#3b82f6', '#10b981', '#475569'],
        chartArea: { left: 20, top: 40, width: '90%', height: '80%' },
        legend: { position: 'bottom', textStyle: { fontSize: 10, color: '#94a3b8' } },
        titleTextStyle: { fontSize: 12, bold: true, color: '#f1f5f9' },
        backgroundColor: 'transparent'
      };

      const chart = new window.google.visualization.PieChart(chartRef.current);
      chart.draw(dataTable, options);
    });
  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center w-full">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">MoSCoW Effort Share</span>
      <div ref={chartRef} className="w-full h-64" />
    </div>
  );
};

// 2. Google Line Chart (North Star Trajectory)
const NorthStarLineChart = ({ trajectory }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!trajectory) return;
    loadGoogleCharts(() => {
      if (!chartRef.current) return;

      const chartData = [['Month', 'Actual NSM (mn)', 'Target NSM (mn)']];
      trajectory.forEach(t => {
        chartData.push([
          t.month, 
          t.actual === null ? null : Number(t.actual), 
          t.target === null ? null : Number(t.target)
        ]);
      });

      const dataTable = window.google.visualization.arrayToDataTable(chartData);

      const options = {
        title: 'North Star Metric Trajectory (mn)',
        curveType: 'function',
        legend: { position: 'bottom', textStyle: { color: '#94a3b8' } },
        colors: ['#6366f1', '#475569'],
        hAxis: { title: 'Month', textStyle: { fontSize: 10, color: '#94a3b8' }, titleTextStyle: { color: '#64748b' } },
        vAxis: { title: 'NSM Orders (mn)', textStyle: { fontSize: 10, color: '#94a3b8' }, titleTextStyle: { color: '#64748b' } },
        chartArea: { left: 50, top: 30, width: '85%', height: '70%' },
        backgroundColor: 'transparent',
        pointsVisible: true
      };

      const chart = new window.google.visualization.LineChart(chartRef.current);
      chart.draw(dataTable, options);
    });
  }, [trajectory]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center w-full">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Trajectory (Actual vs Target)</span>
      <div ref={chartRef} className="w-full h-64" />
    </div>
  );
};

// 3. Google Line Chart (Cohort Retention Curves)
const CohortRetentionChart = ({ data }) => {
  const chartRef = useRef(null);

  useEffect(() => {
    if (!data || !data.calculated) return;
    loadGoogleCharts(() => {
      if (!chartRef.current) return;

      const columns = ['Month', 'Jan Cohort', 'Feb Cohort', 'Mar Cohort', 'Apr Cohort', 'Average'];
      const rows = [];

      for (let m = 0; m <= 6; m++) {
        const key = `m${m}`;
        const row = [`M${m}`];
        
        data.calculated.forEach(c => {
          row.push(c[key] * 100); // Express as percentage
        });
        row.push(data.averages[key] * 100);
        
        rows.push(row);
      }

      const dataTable = new window.google.visualization.DataTable();
      dataTable.addColumn('string', 'Month');
      columns.slice(1).forEach(colName => {
        dataTable.addColumn('number', colName);
      });
      dataTable.addRows(rows);

      const options = {
        title: 'Cohort Retention Curves (%)',
        curveType: 'function',
        legend: { position: 'bottom', textStyle: { fontSize: 9, color: '#94a3b8' } },
        vAxis: { title: 'Retention %', minValue: 0, maxValue: 100, textStyle: { color: '#94a3b8' } },
        hAxis: { title: 'Period', textStyle: { color: '#94a3b8' } },
        chartArea: { left: 45, top: 35, width: '85%', height: '70%' },
        colors: ['#c084fc', '#818cf8', '#38bdf8', '#fb7185', '#f1f5f9'],
        lineWidth: 2.5,
        backgroundColor: 'transparent',
        pointsVisible: true
      };

      const chart = new window.google.visualization.LineChart(chartRef.current);
      chart.draw(dataTable, options);
    });
  }, [data]);

  return (
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col items-center w-full">
      <span className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">Retention Performance (Curves)</span>
      <div ref={chartRef} className="w-full h-64" />
    </div>
  );
};


// ============================================================================
// --- MAIN PLAYGROUND CONTAINER ---
// ============================================================================

const AdminPMPlayground = () => {
  const [activeTab, setActiveTab] = useState('index'); // 'index' | templateKey (e.g. 'RICE')
  const [searchQuery, setSearchQuery] = useState('');

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
        // Retain headers but empty rows
        cleared.rows = [];
      }
      if (cleared.inputs) {
        Object.keys(cleared.inputs).forEach(k => {
          cleared.inputs[k] = 0;
        });
      }
      if (cleared.weights) {
        // Keep weights but zero them
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

  // Editable Grid Cell Updates
  const handleCellChange = (frameworkKey, rowIndex, colKey, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedRows = [...framework.rows];
      updatedRows[rowIndex] = { ...updatedRows[rowIndex], [colKey]: val };
      framework.rows = updatedRows;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleWeightChange = (frameworkKey, weightKey, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      framework.weights = { ...framework.weights, [weightKey]: Number(val) || 0 };
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleInputChange = (frameworkKey, inputKey, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      framework.inputs = { ...framework.inputs, [inputKey]: Number(val) || 0 };
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleDriverChange = (frameworkKey, idx, field, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedDrivers = [...framework.drivers];
      updatedDrivers[idx] = { ...updatedDrivers[idx], [field]: Number(val) || 0 };
      framework.drivers = updatedDrivers;
      return { ...prev, [frameworkKey]: framework };
    });
  };

  const handleTrajectoryChange = (frameworkKey, idx, field, val) => {
    setFrameworkData(prev => {
      const framework = { ...prev[frameworkKey] };
      const updatedTrajectory = [...framework.trajectory];
      updatedTrajectory[idx] = { 
        ...updatedTrajectory[idx], 
        [field]: val === '' ? null : Number(val) 
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
        else if (h.key === "category") newRow[h.key] = "M"; // default MoSCoW
        else if (h.key === "vector") newRow[h.key] = "Market Penetration"; // default Ansoff
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

  // logical groupings for frameworks
  const groups = [
    {
      name: "Prioritisation backlogs",
      icon: <Sliders className="h-4 w-4 text-purple-400" />,
      color: "purple",
      keys: ["RICE", "WeightedScoring", "ValueVsEffort", "KanoModel", "WSJF", "MoSCoW", "OpportunityScoring"]
    },
    {
      name: "Strategy & Portfolios",
      icon: <TrendingUp className="h-4 w-4 text-blue-400" />,
      color: "blue",
      keys: ["BCGMatrix", "AnsoffMatrix", "Porter5Forces"]
    },
    {
      name: "Metrics & Growth loops",
      icon: <Activity className="h-4 w-4 text-emerald-450" />,
      color: "emerald",
      keys: ["NorthStar", "AARRR", "HEART", "UnitEconomics", "CohortRetention", "PMFSurvey"]
    },
    {
      name: "Experiments & OKRs",
      icon: <Target className="h-4 w-4 text-rose-450" />,
      color: "rose",
      keys: ["ABTest", "OKRTracker"]
    }
  ];

  // Flat list of frameworks to search/display in dashboard
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

  const filteredFrameworks = allFrameworksList.filter(f => 
    f.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    f.whenToUse.toLowerCase().includes(searchQuery.toLowerCase()) ||
    f.category.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="w-full bg-slate-900 min-h-screen text-slate-100 rounded-xl overflow-hidden shadow-2xl flex border border-slate-800">
      
      {/* --- PLAYGROUND LEFT NAV SIDEBAR --- */}
      <aside className="w-64 border-r border-slate-800 bg-slate-950 flex flex-col shrink-0">
        <div className="p-4 border-b border-slate-800 flex items-center gap-2">
          <div className="p-2 rounded-lg bg-pink-500/10 border border-pink-500/20 text-pink-500">
            <Activity className="h-5 w-5" />
          </div>
          <div>
            <h2 className="font-bold text-sm text-slate-200">PM Playground</h2>
            <p className="text-[10px] text-slate-500 font-medium">Sandbox Management Center</p>
          </div>
        </div>

        {/* Sidebar Search */}
        <div className="p-3">
          <div className="relative">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-500" />
            <input
              type="text"
              placeholder="Search frameworks..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full bg-slate-900 border border-slate-800 rounded-lg pl-8 pr-3 py-1.5 text-xs text-slate-350 placeholder-slate-500 focus:outline-none focus:border-pink-500/50"
            />
          </div>
        </div>

        {/* Navigation list */}
        <nav className="flex-1 overflow-y-auto px-2 space-y-4 pb-4">
          <button
            onClick={() => setActiveTab('index')}
            className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-semibold transition-colors ${
              activeTab === 'index' 
                ? 'bg-pink-500/10 text-pink-400 border border-pink-500/20' 
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
            }`}
          >
            <BookOpen className="h-4 w-4" />
            <span>Dashboard Home</span>
          </button>

          {groups.map((g, idx) => (
            <div key={idx} className="space-y-1">
              <span className="px-3 text-[10px] font-bold text-slate-500 uppercase tracking-wider block mb-1">
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
                        ? 'bg-slate-850 text-pink-400 font-semibold border-l-2 border-pink-500 pl-4' 
                        : 'text-slate-450 hover:text-slate-200 hover:bg-slate-900/40 pl-3'
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

      {/* --- PLAYGROUND MAIN SECTION --- */}
      <section className="flex-1 flex flex-col bg-slate-900 min-w-0">
        
        {/* TOP BAR */}
        <header className="h-14 border-b border-slate-800 px-6 flex items-center justify-between shrink-0 bg-slate-950/20">
          <div className="flex items-center gap-3">
            {activeTab !== 'index' && (
              <button 
                onClick={() => setActiveTab('index')} 
                className="p-1 rounded bg-slate-850 hover:bg-slate-700 text-slate-350 hover:text-white transition-colors"
                title="Back to Dashboard"
              >
                <ArrowLeft size={16} />
              </button>
            )}
            <h1 className="font-bold text-base text-slate-100">
              {activeTab === 'index' ? "PM Playbook Dashboard" : PM_TEMPLATES[activeTab].title}
            </h1>
          </div>
          
          {activeTab !== 'index' && (
            <div className="flex items-center gap-2">
              <button
                onClick={() => handleReset(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-slate-800 border border-slate-700 hover:bg-slate-750 text-xs font-semibold text-slate-300 hover:text-white transition-all"
              >
                <RotateCcw size={13} />
                <span>Reset to Example</span>
              </button>
              <button
                onClick={() => handleClear(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-rose-950/40 border border-rose-900/40 hover:bg-rose-900/30 text-xs font-semibold text-rose-450 hover:text-rose-350 transition-all"
              >
                <Trash2 size={13} />
                <span>Clear All</span>
              </button>
              <button
                onClick={() => handleExportCSV(activeTab)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-slate-800 border border-slate-700 hover:bg-slate-750 text-xs font-semibold text-slate-300 hover:text-white transition-all"
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
              <div className="relative p-6 rounded-2xl bg-gradient-to-r from-pink-950/20 via-indigo-950/30 to-slate-900 border border-slate-800 overflow-hidden">
                <div className="relative z-10 max-w-2xl">
                  <span className="text-[10px] uppercase font-bold text-pink-400 tracking-wider bg-pink-950/60 px-2.5 py-1 rounded border border-pink-900/40">
                    A Senior Consultant's Field Guide
                  </span>
                  <h2 className="text-2xl font-black text-white mt-3 mb-2">The Product Manager's Framework Playbook</h2>
                  <p className="text-slate-300 text-xs leading-relaxed">
                    Welcome to the Quantitative Assessment Playground. Choose from 18 active formula-based calculators below. Plug in your own figures (yellow fields in sheets) or use the prefilled templates to test your product economics, prioritise backlogs, and run experiments.
                  </p>
                </div>
                <div className="absolute right-0 bottom-0 top-0 w-1/3 bg-[radial-gradient(circle_at_right,#f43f5e20,#00000000)]" />
              </div>

              {/* Grid of groups */}
              <div className="space-y-6">
                {groups.map((g, groupIdx) => {
                  // Filter based on search query
                  const matches = g.keys.filter(k => {
                    const temp = PM_TEMPLATES[k];
                    return temp.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                           temp.whenToUse.toLowerCase().includes(searchQuery.toLowerCase());
                  });
                  
                  if (matches.length === 0) return null;

                  return (
                    <div key={groupIdx} className="space-y-3">
                      <div className="flex items-center gap-2 border-b border-slate-850 pb-2">
                        {g.icon}
                        <h3 className="font-bold text-xs uppercase tracking-wider text-slate-400">
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
                              className="group p-5 rounded-xl bg-slate-950/40 border border-slate-800 hover:border-pink-500/40 transition-all duration-150 cursor-pointer flex flex-col justify-between hover:translate-y-[-2px] hover:shadow-lg"
                            >
                              <div>
                                <div className="flex justify-between items-center mb-2">
                                  <span className={`text-[9px] font-bold px-2 py-0.5 rounded uppercase tracking-wider ${
                                    g.color === 'purple' ? 'bg-purple-950/80 text-purple-300 border border-purple-900' :
                                    g.color === 'blue' ? 'bg-blue-950/80 text-blue-300 border border-blue-900' :
                                    g.color === 'emerald' ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-900' :
                                    'bg-rose-950/80 text-rose-300 border border-rose-900'
                                  }`}>
                                    {template.stage}
                                  </span>
                                  <span className="text-[10px] text-slate-550 font-mono">Calculator Tab</span>
                                </div>
                                <h4 className="font-extrabold text-sm text-slate-100 group-hover:text-pink-400 transition-colors">
                                  {template.title}
                                </h4>
                                <p className="text-slate-400 text-xs mt-2 line-clamp-3 leading-relaxed">
                                  {template.whenToUse}
                                </p>
                              </div>
                              <div className="mt-4 pt-3 border-t border-slate-850 flex justify-between items-center">
                                <span className="text-[10px] text-slate-500 font-semibold group-hover:text-slate-350 transition-colors">
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

            // Render Framework Guidelines, Data Grid, and Chart
            return (
              <div className="space-y-6">
                
                {/* 1. GUIDELINES INFOGRAPHIC PANEL */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Left Column: Descriptions */}
                  <div className="space-y-4">
                    {/* In Plain Words */}
                    <div className="p-4 rounded-xl bg-gradient-to-br from-slate-950 to-indigo-950/40 border border-indigo-900/40">
                      <span className="text-[9px] font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1">
                        <Info size={11} /> In Plain Words
                      </span>
                      <p className="text-xs text-indigo-200 mt-1 font-semibold leading-relaxed">
                        {template.inPlainWords}
                      </p>
                    </div>

                    {/* When to use */}
                    <div className="p-4 rounded-xl bg-slate-955/50 border border-slate-800">
                      <span className="text-[9px] font-bold uppercase tracking-wider text-blue-400 block mb-1">
                        When to Reach for It
                      </span>
                      <p className="text-xs text-slate-300 leading-relaxed">
                        {template.whenToUse}
                      </p>
                    </div>
                  </div>

                  {/* Right Column: Execution steps & Watch outs */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {/* How to run */}
                    <div className="p-4 rounded-xl bg-slate-955/50 border border-slate-800 flex flex-col justify-between">
                      <div>
                        <span className="text-[9px] font-bold uppercase tracking-wider text-emerald-400 block mb-2">
                          How to Run It
                        </span>
                        <ol className="text-[11px] text-slate-400 space-y-1.5 list-decimal pl-4 leading-normal">
                          {template.howToRun.map((step, sIdx) => (
                            <li key={sIdx}>{step}</li>
                          ))}
                        </ol>
                      </div>
                    </div>

                    {/* Watch out for */}
                    <div className="p-4 rounded-xl bg-rose-950/10 border border-rose-900/20 flex flex-col justify-between">
                      <div>
                        <span className="text-[9px] font-bold uppercase tracking-wider text-rose-400 block mb-2">
                          Watch Out For (Traps)
                        </span>
                        <ul className="text-[11px] text-rose-300/80 space-y-1.5 list-disc pl-4 leading-normal">
                          {template.watchOutFor.map((trap, tIdx) => (
                            <li key={tIdx}>{trap}</li>
                          ))}
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 2. DYNAMIC INPUT & CALCULATION GRID */}
                <div className="p-5 rounded-xl bg-slate-950/40 border border-slate-800 space-y-4">
                  <div className="flex justify-between items-center border-b border-slate-850 pb-3">
                    <span className="font-bold text-sm text-slate-200">Assessment Workspace</span>
                    {template.headers && (
                      <button
                        onClick={() => handleAddRow(activeTab)}
                        className="flex items-center gap-1 px-3 py-1.5 rounded bg-pink-600 hover:bg-pink-700 text-xs font-semibold text-white transition-colors"
                      >
                        <Plus size={14} />
                        <span>Add Row</span>
                      </button>
                    )}
                  </div>

                  {/* GRID EDITING WORKSPACE */}
                  {(() => {
                    if (activeTab === "NorthStar") {
                      // North Star is a unique structured scorecard + trajectory table
                      return (
                        <div className="space-y-6">
                          {/* Part 1: Input Drivers */}
                          <div>
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider mb-2">
                              1. Inputs & Drivers Decomposition
                            </h4>
                            <div className="overflow-x-auto">
                              <table className="w-full text-xs text-left border border-slate-850 rounded-lg overflow-hidden">
                                <thead className="bg-slate-900 text-slate-300 font-bold border-b border-slate-800">
                                  <tr>
                                    <th className="p-3">Input Driver</th>
                                    <th className="p-3 bg-yellow-500/10 text-yellow-400">Current</th>
                                    <th className="p-3 bg-yellow-500/10 text-yellow-400">90-Day Target</th>
                                    <th className="p-3 text-right">Numeric Change</th>
                                    <th className="p-3 text-right">% Change</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-850">
                                  {d3.range(3).map(idx => {
                                    const dr = data.drivers[idx];
                                    const calc = template.calculate(data.drivers, data.trajectory).drivers[idx];
                                    return (
                                      <tr key={idx} className="hover:bg-slate-900/30">
                                        <td className="p-3 font-semibold text-slate-350">{dr.driver}</td>
                                        <td className="p-3 bg-yellow-500/5">
                                          <input
                                            type="number"
                                            value={dr.current}
                                            onChange={e => handleDriverChange(activeTab, idx, "current", e.target.value)}
                                            className="w-24 bg-slate-900 border border-slate-700 rounded p-1 text-center font-bold text-yellow-400"
                                          />
                                        </td>
                                        <td className="p-3 bg-yellow-500/5">
                                          <input
                                            type="number"
                                            value={dr.target}
                                            onChange={e => handleDriverChange(activeTab, idx, "target", e.target.value)}
                                            className="w-24 bg-slate-900 border border-slate-700 rounded p-1 text-center font-bold text-yellow-400"
                                          />
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold text-slate-300">
                                          {calc.change > 0 ? `+${calc.change}` : calc.change}
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold text-slate-300">
                                          {(calc.pct * 100).toFixed(1)}%
                                        </td>
                                      </tr>
                                    );
                                  })}
                                  {/* Result Summary row */}
                                  {(() => {
                                    const summary = template.calculate(data.drivers, data.trajectory).summary;
                                    return (
                                      <tr className="bg-slate-900 border-t border-slate-750">
                                        <td className="p-3 font-extrabold text-slate-200">
                                          NORTH STAR (monthly on-time orders, mn)
                                        </td>
                                        <td className="p-3 text-center font-mono font-extrabold text-slate-200">
                                          {summary.current.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-center font-mono font-extrabold text-slate-200">
                                          {summary.target.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-right font-mono font-extrabold text-slate-300">
                                          +{summary.change.toFixed(4)}
                                        </td>
                                        <td className="p-3 text-right font-mono font-extrabold text-emerald-400">
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
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider mb-2">
                              2. Monthly Trajectory Actual vs Target
                            </h4>
                            <div className="overflow-x-auto max-h-60">
                              <table className="w-full text-xs text-left border border-slate-850 rounded-lg overflow-hidden">
                                <thead className="bg-slate-900 text-slate-300 font-bold border-b border-slate-800">
                                  <tr>
                                    <th className="p-2.5">Month</th>
                                    <th className="p-2.5 bg-yellow-500/10 text-yellow-400">NSM Actual (mn)</th>
                                    <th className="p-2.5 bg-yellow-500/10 text-yellow-400">NSM Target (mn)</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-850">
                                  {data.trajectory.map((t, idx) => (
                                    <tr key={idx} className="hover:bg-slate-900/30">
                                      <td className="p-2 font-bold text-slate-400">{t.month}</td>
                                      <td className="p-2 bg-yellow-500/5">
                                        <input
                                          type="number"
                                          placeholder="—"
                                          value={t.actual === null ? '' : t.actual}
                                          onChange={e => handleTrajectoryChange(activeTab, idx, "actual", e.target.value)}
                                          className="w-28 bg-slate-900 border border-slate-700 rounded p-1 text-center font-semibold text-yellow-400"
                                        />
                                      </td>
                                      <td className="p-2 bg-yellow-500/5">
                                        <input
                                          type="number"
                                          placeholder="—"
                                          value={t.target === null ? '' : t.target}
                                          onChange={e => handleTrajectoryChange(activeTab, idx, "target", e.target.value)}
                                          className="w-28 bg-slate-900 border border-slate-700 rounded p-1 text-center font-semibold text-yellow-400"
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
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider">
                              Calculator Inputs
                            </h4>
                            <div className="space-y-3 bg-slate-900/40 p-4 rounded-xl border border-slate-850">
                              <div>
                                <label className="block text-xs text-slate-450 mb-1">
                                  ARPU (Average Revenue Per User / month)
                                </label>
                                <input
                                  type="number"
                                  value={data.inputs.arpu}
                                  onChange={e => handleInputChange(activeTab, "arpu", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-yellow-400 font-bold text-sm"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-450 mb-1">
                                  Gross Margin (e.g. 0.30 for 30%)
                                </label>
                                <input
                                  type="number"
                                  step="0.05"
                                  value={data.inputs.margin}
                                  onChange={e => handleInputChange(activeTab, "margin", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-yellow-400 font-bold text-sm"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-450 mb-1">
                                  Monthly Churn Rate (e.g. 0.05 for 5%)
                                </label>
                                <input
                                  type="number"
                                  step="0.01"
                                  value={data.inputs.churn}
                                  onChange={e => handleInputChange(activeTab, "churn", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-yellow-400 font-bold text-sm"
                                />
                              </div>
                              <div>
                                <label className="block text-xs text-slate-450 mb-1">
                                  CAC (Customer Acquisition Cost)
                                </label>
                                <input
                                  type="number"
                                  value={data.inputs.cac}
                                  onChange={e => handleInputChange(activeTab, "cac", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-2 text-yellow-400 font-bold text-sm"
                                />
                              </div>
                            </div>
                          </div>

                          {/* Calculations & Results */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider">
                              Results & Sensitivity Analysis
                            </h4>
                            
                            {/* KPI Metrics */}
                            <div className="grid grid-cols-2 gap-3">
                              <div className="bg-slate-900/50 p-3 rounded-lg border border-slate-800 text-center">
                                <span className="text-[10px] text-slate-500 font-bold uppercase">Customer Lifetime</span>
                                <div className="text-lg font-black text-slate-200 mt-1">{res.lifetime} mo</div>
                              </div>
                              <div className="bg-slate-900/50 p-3 rounded-lg border border-slate-800 text-center">
                                <span className="text-[10px] text-slate-500 font-bold uppercase">LTV (Lifetime Value)</span>
                                <div className="text-lg font-black text-slate-200 mt-1">₹{res.ltv.toFixed(1)}</div>
                              </div>
                              <div className="bg-slate-900/50 p-3 rounded-lg border border-slate-800 text-center">
                                <span className="text-[10px] text-slate-500 font-bold uppercase">CAC Payback</span>
                                <div className="text-lg font-black text-slate-200 mt-1">{res.payback} mo</div>
                              </div>
                              <div className="bg-slate-900/50 p-3 rounded-lg border border-slate-800 text-center">
                                <span className="text-[10px] text-slate-500 font-bold uppercase">LTV:CAC Ratio</span>
                                <div className={`text-lg font-black mt-1 ${
                                  res.ratio >= 3 ? 'text-emerald-400' :
                                  res.ratio >= 1 ? 'text-amber-400' : 'text-rose-500'
                                }`}>
                                  {res.ratio}x
                                  <span className="text-[9px] block text-slate-400 font-semibold">{res.verdict}</span>
                                </div>
                              </div>
                            </div>

                            {/* Sensitivity table */}
                            <div className="bg-slate-900/50 p-3.5 rounded-lg border border-slate-800">
                              <span className="text-[10px] font-extrabold uppercase text-slate-450 block mb-2">
                                Churn Sensitivity heatmap
                              </span>
                              <table className="w-full text-center text-xs">
                                <thead>
                                  <tr className="text-slate-500 border-b border-slate-800">
                                    <th className="pb-1 text-left">Monthly Churn</th>
                                    <th className="pb-1">Implied LTV</th>
                                    <th className="pb-1">LTV:CAC Ratio</th>
                                  </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-850">
                                  {res.sensitivity.map((sens, sIdx) => (
                                    <tr key={sIdx} className="hover:bg-slate-900/20">
                                      <td className="py-2 text-left font-bold text-slate-400">{(sens.churn * 100).toFixed(0)}%</td>
                                      <td className="py-2 font-mono text-slate-350">₹{sens.ltv}</td>
                                      <td className={`py-2 font-mono font-bold ${
                                        sens.ratio >= 3 ? 'text-emerald-400' :
                                        sens.ratio >= 1.5 ? 'text-blue-400' : 'text-rose-500'
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
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider">
                              Traffic & Conversions
                            </h4>
                            <div className="grid grid-cols-2 gap-4 bg-slate-900/40 p-4 rounded-xl border border-slate-850">
                              <div className="col-span-2 border-b border-slate-800 pb-1.5">
                                <span className="text-[10px] font-bold uppercase text-slate-500">Control (A)</span>
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-450 mb-1">Visitors</label>
                                <input
                                  type="number"
                                  value={data.inputs.controlVisitors}
                                  onChange={e => handleInputChange(activeTab, "controlVisitors", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-1.5 text-yellow-400 font-bold"
                                />
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-450 mb-1">Conversions</label>
                                <input
                                  type="number"
                                  value={data.inputs.controlConversions}
                                  onChange={e => handleInputChange(activeTab, "controlConversions", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-1.5 text-yellow-400 font-bold"
                                />
                              </div>

                              <div className="col-span-2 border-b border-slate-800 pb-1.5 mt-2">
                                <span className="text-[10px] font-bold uppercase text-indigo-400">Variant (B)</span>
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-450 mb-1">Visitors</label>
                                <input
                                  type="number"
                                  value={data.inputs.variantVisitors}
                                  onChange={e => handleInputChange(activeTab, "variantVisitors", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-1.5 text-yellow-400 font-bold"
                                />
                              </div>
                              <div>
                                <label className="block text-[10px] text-slate-450 mb-1">Conversions</label>
                                <input
                                  type="number"
                                  value={data.inputs.variantConversions}
                                  onChange={e => handleInputChange(activeTab, "variantConversions", e.target.value)}
                                  className="w-full bg-slate-900 border border-slate-700 rounded p-1.5 text-yellow-400 font-bold"
                                />
                              </div>
                            </div>
                          </div>

                          {/* Calculations & Results */}
                          <div className="space-y-4">
                            <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider">
                              Significance Results
                            </h4>
                            
                            <div className="space-y-3 bg-slate-900/50 p-4 rounded-xl border border-slate-850">
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-450">Control Conv. Rate:</span>
                                <span className="font-mono font-bold text-slate-200">{(res.cr * 100).toFixed(2)}%</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-450">Variant Conv. Rate:</span>
                                <span className="font-mono font-bold text-slate-200">{(res.vr * 100).toFixed(2)}%</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-450">Relative Uplift:</span>
                                <span className={`font-mono font-extrabold ${res.uplift >= 0 ? 'text-emerald-400' : 'text-rose-500'}`}>
                                  {res.uplift >= 0 ? `+${(res.uplift * 100).toFixed(1)}%` : `${(res.uplift * 100).toFixed(1)}%`}
                                </span>
                              </div>
                              <div className="flex justify-between text-xs border-t border-slate-800 pt-2">
                                <span className="text-slate-450">Z-Score:</span>
                                <span className="font-mono font-extrabold text-indigo-400">{res.zScore}</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-450">Significant at 95%? (|z|&gt;1.96):</span>
                                <span className={`font-bold ${res.sig95 === "YES ✓" ? "text-emerald-400" : "text-slate-500"}`}>{res.sig95}</span>
                              </div>
                              <div className="flex justify-between text-xs">
                                <span className="text-slate-450">Significant at 99%? (|z|&gt;2.576):</span>
                                <span className={`font-bold ${res.sig99 === "YES ✓" ? "text-emerald-400" : "text-slate-500"}`}>{res.sig99}</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      );
                    }

                    // STANDARD TABULAR GRID FOR OTHER CALCULATORS
                    const finalRows = template.calculate ? template.calculate(data.rows, data.weights).rows || template.calculate(data.rows, data.weights) : data.rows;
                    
                    // Specific to Cohort Retention, calculate stats first
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
                          <div className="p-4 rounded-xl bg-slate-900/40 border border-slate-850 flex items-center gap-4 flex-wrap text-xs">
                            <span className="font-bold text-slate-355">Adjust Criteria Weights (Sum must equal 100%):</span>
                            {Object.keys(data.weights).map(wKey => {
                              const labels = { fit: "Strategic Fit", revenue: "Revenue Upside", speed: "Speed to Ship", risk: "Low Risk" };
                              return (
                                <div key={wKey} className="flex items-center gap-1.5">
                                  <span className="text-slate-400 font-semibold">{labels[wKey]}:</span>
                                  <input
                                    type="number"
                                    step="0.05"
                                    value={data.weights[wKey]}
                                    onChange={e => handleWeightChange(activeTab, wKey, e.target.value)}
                                    className="w-14 bg-slate-900 border border-slate-700 rounded p-1 text-center font-bold text-yellow-400"
                                  />
                                </div>
                              );
                            })}
                            {(() => {
                              const sum = Object.values(data.weights).reduce((a, b) => a + b, 0);
                              const isOk = Math.abs(sum - 1.0) < 0.001;
                              return (
                                <span className={`font-bold ml-auto px-2 py-0.5 rounded text-[10px] ${isOk ? 'bg-emerald-950 text-emerald-400 border border-emerald-900' : 'bg-rose-950 text-rose-400 border border-rose-900'}`}>
                                  {isOk ? "Sum: 100% ✓" : `Sum: ${(sum*100).toFixed(0)}% (FIX)`}
                                </span>
                              );
                            })()}
                          </div>
                        )}

                        {/* RENDER TABLE CONTAINER */}
                        <div className="overflow-x-auto">
                          <table className="w-full text-xs text-left border border-slate-850 rounded-lg overflow-hidden">
                            <thead className="bg-slate-900 text-slate-300 font-bold border-b border-slate-800">
                              <tr>
                                {template.headers.map((h, hIdx) => (
                                  <th 
                                    key={hIdx} 
                                    className={`p-3 ${h.editable ? 'bg-yellow-500/10 text-yellow-400' : 'text-slate-300'} ${
                                      h.type === 'number' || h.type === 'percent' ? 'text-center' : ''
                                    }`}
                                  >
                                    {h.label}
                                  </th>
                                ))}
                                <th className="p-3 w-10 text-center"></th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-850">
                              {processedData.map((row, rIdx) => (
                                <tr key={row.id || rIdx} className="hover:bg-slate-900/35">
                                  {template.headers.map((h, hIdx) => {
                                    const val = row[h.key];
                                    
                                    if (h.editable) {
                                      return (
                                        <td key={hIdx} className="p-2 bg-yellow-500/5">
                                          {h.key === "category" ? (
                                            <select
                                              value={val}
                                              onChange={e => handleCellChange(activeTab, rIdx, h.key, e.target.value)}
                                              className="w-full bg-slate-900 border border-slate-700 rounded p-1 text-slate-350 font-bold"
                                            >
                                              {h.options.map(opt => <option key={opt} value={opt}>{opt}</option>)}
                                            </select>
                                          ) : h.key === "vector" ? (
                                            <select
                                              value={val}
                                              onChange={e => handleCellChange(activeTab, rIdx, h.key, e.target.value)}
                                              className="w-full bg-slate-900 border border-slate-700 rounded p-1 text-slate-355 font-bold"
                                            >
                                              {h.options.map(opt => <option key={opt} value={opt}>{opt}</option>)}
                                            </select>
                                          ) : (
                                            <input
                                              type={h.type === "number" || h.type === "percent" ? "number" : "text"}
                                              step={h.type === "percent" ? "0.05" : "1"}
                                              value={val}
                                              onChange={e => {
                                                const v = h.type === "number" || h.type === "percent" ? Number(e.target.value) : e.target.value;
                                                handleCellChange(activeTab, rIdx, h.key, v);
                                              }}
                                              className={`w-full bg-slate-900 border border-slate-700 rounded p-1 font-semibold text-yellow-400 ${
                                                h.type === "number" || h.type === "percent" ? "text-center" : "text-left"
                                              }`}
                                            />
                                          )}
                                        </td>
                                      );
                                    } else {
                                      // Render read-only calculated cell
                                      let displayVal = val;
                                      if (h.type === "percent") {
                                        displayVal = (val * 100).toFixed(0) + "%";
                                      } else if (typeof val === "number" && !Number.isInteger(val)) {
                                        displayVal = val.toFixed(2);
                                      }
                                      return (
                                        <td 
                                          key={hIdx} 
                                          className={`p-3 font-mono font-bold text-slate-300 ${
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
                                        className="text-slate-500 hover:text-rose-500 p-1 rounded hover:bg-rose-500/10 transition-colors"
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
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 rounded-xl bg-slate-900/40 border border-slate-850 text-xs">
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Must-have Effort:</span>
                              <span className="text-sm font-extrabold text-slate-200">{moscowStats.summary.M.effort} days ({moscowStats.summary.M.count} items)</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Should-have Effort:</span>
                              <span className="text-sm font-extrabold text-slate-200">{moscowStats.summary.S.effort} days ({moscowStats.summary.S.count} items)</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Total Effort:</span>
                              <span className="text-sm font-extrabold text-slate-200">{moscowStats.totalEffort} days</span>
                            </div>
                            <div className="space-y-1">
                              <span className="text-slate-500 font-bold block">Must-have % of Effort:</span>
                              <span className={`text-sm font-extrabold ${moscowStats.mustPercent <= 60 ? 'text-emerald-400' : 'text-rose-500'}`}>
                                {moscowStats.mustPercent.toFixed(1)}% 
                                {moscowStats.mustPercent <= 60 ? ' (Healthy ✓)' : ' (Too high - risk!)'}
                              </span>
                            </div>
                          </div>
                        )}

                        {activeTab === "PMFSurvey" && (() => {
                          const surveyStats = template.calculate(data.rows);
                          return (
                            <div className="grid grid-cols-3 gap-4 p-4 rounded-xl bg-slate-900/40 border border-slate-850 text-xs text-center">
                              <div>
                                <span className="text-slate-550 font-bold block uppercase">Valid Responses</span>
                                <span className="text-lg font-black text-slate-200 mt-1">{surveyStats.valid}</span>
                              </div>
                              <div>
                                <span className="text-slate-550 font-bold block uppercase">PMF Score</span>
                                <span className={`text-lg font-black mt-1 ${surveyStats.score >= 0.40 ? 'text-emerald-400' : 'text-rose-500'}`}>
                                  {(surveyStats.score * 100).toFixed(1)}%
                                </span>
                              </div>
                              <div>
                                <span className="text-slate-550 font-bold block uppercase">Verdict (40% Bench)</span>
                                <span className={`text-lg font-black mt-1 ${surveyStats.score >= 0.40 ? 'text-emerald-400' : 'text-rose-500'}`}>
                                  {surveyStats.verdict}
                                </span>
                              </div>
                            </div>
                          );
                        })()}

                        {activeTab === "CohortRetention" && (() => {
                          const retentionStats = template.calculate(data.rows);
                          return (
                            <div>
                              <h4 className="text-xs font-extrabold uppercase text-slate-400 tracking-wider mb-2">
                                2. Cohort Retention Heatmap (%)
                              </h4>
                              <div className="overflow-x-auto">
                                <table className="w-full text-xs text-center border border-slate-850 rounded-lg overflow-hidden">
                                  <thead className="bg-slate-900 text-slate-350 font-bold border-b border-slate-800">
                                    <tr>
                                      <th className="p-2.5 text-left">Cohort</th>
                                      <th className="p-2.5">Size (M0)</th>
                                      <th className="p-2.5">M1</th>
                                      <th className="p-2.5">M2</th>
                                      <th className="p-2.5">M3</th>
                                      <th className="p-2.5">M4</th>
                                      <th className="p-2.5">M5</th>
                                      <th className="p-2.5">M6</th>
                                    </tr>
                                  </thead>
                                  <tbody className="divide-y divide-slate-850">
                                    {retentionStats.calculated.map((cRow, cIdx) => (
                                      <tr key={cIdx} className="hover:bg-slate-900/20">
                                        <td className="p-2 text-left font-bold text-slate-450">{cRow.cohort}</td>
                                        <td className="p-2 font-semibold text-slate-300">{cRow.size}</td>
                                        {d3.range(7).map(mNum => {
                                          const key = `m${mNum}`;
                                          const val = cRow[key];
                                          // Heatmap coloring based on percentage
                                          const red = 15 + Math.round((1 - val) * 20);
                                          const green = 23 + Math.round(val * 110);
                                          const blue = 42 + Math.round(val * 50);
                                          return (
                                            <td 
                                              key={mNum} 
                                              style={{ backgroundColor: `rgba(${red}, ${green}, ${blue}, 0.28)` }}
                                              className="p-2.5 font-bold text-slate-200 border border-slate-850/60"
                                            >
                                              {mNum === 0 ? "100%" : (val * 100).toFixed(0) + "%"}
                                            </td>
                                          );
                                        })}
                                      </tr>
                                    ))}
                                    {/* Average Cohort Row */}
                                    <tr className="bg-slate-900 font-extrabold border-t border-slate-800">
                                      <td className="p-2 text-left text-slate-300">Average</td>
                                      <td className="p-2 text-slate-500">—</td>
                                      <td className="p-2 text-indigo-300">{(retentionStats.averages.m0 * 100)}%</td>
                                      <td className="p-2 text-indigo-300">{(retentionStats.averages.m1 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-300">{(retentionStats.averages.m2 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-400">{(retentionStats.averages.m3 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-400">{(retentionStats.averages.m4 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-500">{(retentionStats.averages.m5 * 100).toFixed(0)}%</td>
                                      <td className="p-2 text-indigo-500">{(retentionStats.averages.m6 * 100).toFixed(0)}%</td>
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
                    if (activeTab === "RICE") return <RiceBarChart data={frameworkData.RICE.rows} />;
                    if (activeTab === "WeightedScoring") return <WeightedBarChart data={frameworkData.WeightedScoring.rows} />;
                    if (activeTab === "ValueVsEffort") return <ValueVsEffortChart data={frameworkData.ValueVsEffort.rows} />;
                    if (activeTab === "KanoModel") return <KanoScatterPlot data={PM_TEMPLATES.KanoModel.calculate(frameworkData.KanoModel.rows)} />;
                    if (activeTab === "WSJF") {
                      const finalWsjf = PM_TEMPLATES.WSJF.calculate(frameworkData.WSJF.rows);
                      return <RiceBarChart data={finalWsjf.map(d => ({ name: d.name, score: d.wsjf }))} />;
                    }
                    if (activeTab === "MoSCoW") {
                      const stats = PM_TEMPLATES.MoSCoW.calculate(frameworkData.MoSCoW.rows);
                      return <MoscowPieChart data={stats} />;
                    }
                    if (activeTab === "OpportunityScoring") {
                      const finalOpp = PM_TEMPLATES.OpportunityScoring.calculate(frameworkData.OpportunityScoring.rows);
                      return <WeightedBarChart data={finalOpp.map(d => ({ name: d.name, score: d.opportunity }))} />;
                    }
                    if (activeTab === "BCGMatrix") {
                      return <BCGBubbleChart data={PM_TEMPLATES.BCGMatrix.calculate(frameworkData.BCGMatrix.rows)} />;
                    }
                    if (activeTab === "AnsoffMatrix") {
                      const finalAnsoff = PM_TEMPLATES.AnsoffMatrix.calculate(frameworkData.AnsoffMatrix.rows);
                      return <WeightedBarChart data={finalAnsoff.map(d => ({ name: d.name, score: d.riskAdjusted }))} />;
                    }
                    if (activeTab === "Porter5Forces") {
                      return <PorterRadarChart data={frameworkData.Porter5Forces.rows} />;
                    }
                    if (activeTab === "NorthStar") {
                      return <NorthStarLineChart trajectory={frameworkData.NorthStar.trajectory} />;
                    }
                    if (activeTab === "AARRR") {
                      const finalAarrr = PM_TEMPLATES.AARRR.calculate(frameworkData.AARRR.rows);
                      return <AARRRFunnelChart data={finalAarrr} />;
                    }
                    if (activeTab === "HEART") {
                      const finalHeart = PM_TEMPLATES.HEART.calculate(frameworkData.HEART.rows);
                      return <WeightedBarChart data={finalHeart.map(d => ({ name: d.category, score: d.attainment * 100 }))} />;
                    }
                    if (activeTab === "UnitEconomics") {
                      const ueStats = PM_TEMPLATES.UnitEconomics.calculate(frameworkData.UnitEconomics.inputs);
                      return <WeightedBarChart data={ueStats.sensitivity.map(s => ({ name: `${s.churn * 100}% Churn`, score: s.ratio }))} />;
                    }
                    if (activeTab === "CohortRetention") {
                      const finalCohort = PM_TEMPLATES.CohortRetention.calculate(frameworkData.CohortRetention.rows);
                      return <CohortRetentionChart data={finalCohort} />;
                    }
                    if (activeTab === "PMFSurvey") {
                      const finalPmf = PM_TEMPLATES.PMFSurvey.calculate(frameworkData.PMFSurvey.rows);
                      return <ABTestChart data={{
                        cr: finalPmf.score, vr: 0.40, inputs: { controlVisitors: finalPmf.valid, controlConversions: 0, variantVisitors: 100, variantConversions: 40 }
                      }} />;
                    }
                    if (activeTab === "ABTest") {
                      const abStats = PM_TEMPLATES.ABTest.calculate(frameworkData.ABTest.inputs);
                      return <ABTestChart data={abStats} />;
                    }
                    if (activeTab === "OKRTracker") {
                      const finalOkrs = PM_TEMPLATES.OKRTracker.calculate(frameworkData.OKRTracker.rows);
                      return <RiceBarChart data={finalOkrs.map(d => ({ name: d.kr, score: d.progress * 100 }))} />;
                    }
                    
                    return null;
                  })()}

                  {/* Right Column: Key takeaways or analysis */}
                  <div className="bg-slate-950/40 p-5 rounded-xl border border-slate-800 flex flex-col justify-between">
                    <div>
                      <div className="flex items-center gap-2 mb-3 text-pink-400">
                        <TrendingUp size={16} />
                        <h4 className="text-xs font-bold uppercase tracking-wider">Playbook Readout & Verdict</h4>
                      </div>
                      <p className="text-xs text-slate-350 leading-relaxed font-semibold mb-4 bg-slate-900/60 p-3 rounded-lg border-l-4 border-pink-500">
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
                      
                      <div className="space-y-2 mt-4 text-[11px] text-slate-400 leading-relaxed bg-slate-900/20 p-3 rounded border border-slate-800">
                        <span className="font-bold text-slate-350 block mb-1">Methodology Checklist:</span>
                        <div className="flex items-start gap-1.5">
                          <CheckCircle2 size={13} className="text-pink-500 mt-0.5 shrink-0" />
                          <span>Calculations recalculated in real-time on client side.</span>
                        </div>
                        <div className="flex items-start gap-1.5">
                          <CheckCircle2 size={13} className="text-pink-500 mt-0.5 shrink-0" />
                          <span>Example datasets populated directly from field playbook.</span>
                        </div>
                        <div className="flex items-start gap-1.5">
                          <CheckCircle2 size={13} className="text-pink-500 mt-0.5 shrink-0" />
                          <span>Persistent storage enabled (saves automatically to browser cache).</span>
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

    </div>
  );
};

export default AdminPMPlayground;
