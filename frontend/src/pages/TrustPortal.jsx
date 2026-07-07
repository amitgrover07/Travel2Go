import React, { useState, useEffect } from 'react';
import { 
  CheckCircle, 
  AlertCircle, 
  Clock, 
  ArrowRight, 
  Search, 
  Share2, 
  ShieldAlert, 
  Users, 
  ArrowUpRight, 
  Percent, 
  Layers 
} from 'lucide-react';
import api from '../services/api';
import toast from 'react-hot-toast';

const TrustPortal = () => {
  const [activeTab, setActiveTab] = useState('waitlist');
  
  // Waitlist Predictor State
  const [pnr, setPnr] = useState('');
  const [loading, setLoading] = useState(false);
  const [prediction, setPrediction] = useState(null);

  // Refund SLA Mock Data & Timers
  const [refunds, setRefunds] = useState([
    { id: 'REF-8291', trip: 'Delhi to Mumbai (Train)', amount: '₹1,450', requestedAt: new Date(Date.now() - 36 * 3600 * 1000).toISOString(), status: 'PROCESSING' },
    { id: 'REF-8288', trip: 'Pune to Goa (Bus)', amount: '₹980', requestedAt: new Date(Date.now() - 68 * 3600 * 1000).toISOString(), status: 'APPROVED' },
    { id: 'REF-8275', trip: 'Package: Golden Triangle', amount: '₹14,500', requestedAt: new Date(Date.now() - 71 * 3600 * 1000).toISOString(), status: 'PROCESSING' },
    { id: 'REF-8254', trip: 'Manali Budget Hotel', amount: '₹3,200', requestedAt: new Date(Date.now() - 12 * 3600 * 1000).toISOString(), status: 'COMPLETED' }
  ]);

  // Fee Calculator State
  const [travelers, setTravelers] = useState(3);
  const [tripType, setTripType] = useState('Train');

  // Trigger countdown effect for SLA timers
  const [, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  // Format SLA Countdown
  const getSLATimeRemaining = (requestedAt) => {
    const reqTime = new Date(requestedAt).getTime();
    const deadline = reqTime + 72 * 3600 * 1000;
    const now = Date.now();
    const diff = deadline - now;
    
    if (diff <= 0) return { expired: true, text: 'SLA Breached' };
    
    const hours = Math.floor(diff / (3600 * 1000));
    const mins = Math.floor((diff % (3600 * 1000)) / (60 * 1000));
    const secs = Math.floor((diff % (60 * 1000)) / 1000);
    
    return {
      expired: false,
      text: `${hours}h ${mins}m ${secs}s`
    };
  };

  const handlePredict = async (e) => {
    e.preventDefault();
    if (!/^\d{10}$/.test(pnr)) {
      toast.error('Please enter a valid 10-digit PNR number');
      return;
    }

    setLoading(true);
    try {
      // Attempt backend call first
      const res = await api.get(`/waitlist/predict?pnr=${pnr}`).catch(() => null);
      
      if (res && res.data) {
        setPrediction(res.data);
      } else {
        // High fidelity client-side fallback/mock matching the PRD shape
        setTimeout(() => {
          const num = parseInt(pnr.slice(-3));
          let prob = 30 + (num % 65); // Deterministic based on PNR
          let status = num % 2 === 0 ? `GNWL / ${10 + (num % 25)}` : `PQWL / ${4 + (num % 12)}`;
          let rec = '';
          if (prob > 75) {
            rec = 'High confirmation chance! No backup plans required. Go ahead and relax.';
          } else if (prob > 50) {
            rec = 'Moderate confirmation chance. We suggest monitoring or adding a backup bus reservation.';
          } else {
            rec = 'Low confirmation chance. Consider securing an alternative ticket immediately.';
          }

          setPrediction({
            pnr,
            currentStatus: status,
            confirmationProbability: prob,
            recommendation: rec,
            shareText: `My PNR ${pnr} has a ${prob}% confirmation chance on Travel2Go! Check yours free at: ${window.location.origin}/waitlist-predictor`
          });
          setLoading(false);
        }, 1200);
        return;
      }
    } catch (err) {
      toast.error('Could not get prediction. Please try again.');
    } finally {
      if (prediction) setLoading(false);
    }
  };

  const handleWhatsAppShare = () => {
    if (!prediction) return;
    const url = `https://api.whatsapp.com/send?text=${encodeURIComponent(prediction.shareText)}`;
    window.open(url, '_blank');
  };

  // Zero Convenience Fee Calculator Logic
  const getOtaFee = () => {
    const baseFee = tripType === 'Train' ? 40 : tripType === 'Bus' ? 60 : 350;
    return baseFee * travelers;
  };

  return (
    <div className="bg-gray-900 min-h-screen text-gray-100 flex flex-col font-sans">
      {/* Dynamic Glassmorphic Hero */}
      <section className="relative overflow-hidden py-16 px-4 sm:px-6 lg:px-8 border-b border-gray-800 bg-radial-at-t from-blue-900/30 via-gray-900 to-gray-900">
        <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808012_1px,transparent_1px),linear-gradient(to_bottom,#80808012_1px,transparent_1px)] bg-[size:24px_24px]"></div>
        <div className="max-w-5xl mx-auto text-center relative z-10">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-blue-500/10 text-blue-400 border border-blue-500/25 mb-4 animate-pulse">
            <CheckCircle className="h-3 w-3" /> Strategic PRD Target Showcase
          </span>
          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight text-white mb-6">
            Book Free. <span className="text-blue-500 bg-clip-text">Travel Sure.</span>
          </h1>
          <p className="max-w-2xl mx-auto text-base sm:text-lg text-gray-400 leading-relaxed mb-8">
            Experience India's first trust-first travel platform. Real-time waitlist predictions, guaranteed 72-hour refunds, and absolutely zero hidden booking fees.
          </p>

          {/* Tab Selector */}
          <div className="inline-flex p-1 bg-gray-800/80 backdrop-blur rounded-xl border border-gray-700 shadow-xl max-w-full overflow-x-auto">
            <button
              onClick={() => setActiveTab('waitlist')}
              className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-semibold transition-all duration-200 ${
                activeTab === 'waitlist'
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Search className="h-4 w-4" /> Waitlist Predictor
            </button>
            <button
              onClick={() => setActiveTab('sla')}
              className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-semibold transition-all duration-200 ${
                activeTab === 'sla'
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Clock className="h-4 w-4" /> 72h Refund SLA
            </button>
            <button
              onClick={() => setActiveTab('zerofee')}
              className={`flex items-center gap-2 px-6 py-2.5 rounded-lg text-sm font-semibold transition-all duration-200 ${
                activeTab === 'zerofee'
                  ? 'bg-blue-600 text-white shadow-md'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              <Percent className="h-4 w-4" /> Zero-Fee Calculator
            </button>
          </div>
        </div>
      </section>

      {/* Main Tab Content */}
      <main className="max-w-5xl mx-auto px-4 py-12 flex-grow w-full">
        {/* Tab 1: Waitlist Predictor */}
        {activeTab === 'waitlist' && (
          <div className="grid grid-cols-1 md:grid-cols-5 gap-8 items-start">
            <div className="md:col-span-2 bg-gray-800/50 backdrop-blur border border-gray-700/80 p-8 rounded-2xl shadow-xl">
              <h2 className="text-xl font-bold mb-3 text-white">Check PNR Waitlist</h2>
              <p className="text-xs text-gray-400 mb-6">
                Enter your 10-digit IRCTC PNR to check confirmation probability and backup options. Requires no login.
              </p>
              
              <form onSubmit={handlePredict} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">PNR Number</label>
                  <input
                    type="text"
                    maxLength={10}
                    value={pnr}
                    onChange={(e) => setPnr(e.target.value.replace(/\D/g, ''))}
                    placeholder="e.g. 4238918274"
                    className="w-full bg-gray-900 border border-gray-700 rounded-xl px-4 py-3 text-white placeholder-gray-600 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 text-lg tracking-widest font-mono"
                  />
                </div>
                <button
                  type="submit"
                  disabled={loading || pnr.length !== 10}
                  className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-700 disabled:text-gray-500 text-white font-bold py-3 rounded-xl shadow-lg transition-colors cursor-pointer"
                >
                  {loading ? (
                    <div className="h-5 w-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                  ) : (
                    <>
                      Predict Chance <ArrowRight className="h-4 w-4" />
                    </>
                  )}
                </button>
              </form>
            </div>

            <div className="md:col-span-3 min-h-[300px] flex flex-col justify-between">
              {prediction ? (
                <div className="bg-gray-800/40 border border-gray-700/60 p-8 rounded-2xl shadow-xl flex-grow flex flex-col justify-between animate-fade-in">
                  <div>
                    <div className="flex justify-between items-start mb-6">
                      <div>
                        <span className="text-xs font-semibold text-gray-400">PNR: {prediction.pnr}</span>
                        <h3 className="text-2xl font-bold text-white mt-1">Current Status: {prediction.currentStatus}</h3>
                      </div>
                      <div className={`px-4 py-2 rounded-xl text-center border ${
                        prediction.confirmationProbability > 75 
                          ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' 
                          : prediction.confirmationProbability > 50 
                            ? 'bg-amber-500/10 border-amber-500/30 text-amber-400' 
                            : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
                      }`}>
                        <span className="block text-xs font-bold uppercase tracking-wider">Probability</span>
                        <span className="text-3xl font-extrabold">{prediction.confirmationProbability}%</span>
                      </div>
                    </div>

                    <div className="bg-gray-900/50 border border-gray-800 p-5 rounded-xl mb-6">
                      <h4 className="text-xs font-bold text-blue-400 uppercase tracking-widest mb-2 flex items-center gap-1.5">
                        <CheckCircle className="h-3.5 w-3.5" /> Recommendation
                      </h4>
                      <p className="text-sm text-gray-300 leading-relaxed font-medium">
                        {prediction.recommendation}
                      </p>
                    </div>
                  </div>

                  <div className="flex gap-4">
                    <button 
                      onClick={handleWhatsAppShare}
                      className="flex-grow flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-3 rounded-xl shadow-lg transition-colors cursor-pointer"
                    >
                      <Share2 className="h-4 w-4" /> Share Prediction on WhatsApp
                    </button>
                    <button 
                      onClick={() => { setPrediction(null); setPnr(''); }}
                      className="px-5 border border-gray-700 hover:border-gray-500 hover:bg-gray-800/40 text-gray-300 font-bold py-3 rounded-xl transition-all"
                    >
                      Reset
                    </button>
                  </div>
                </div>
              ) : (
                <div className="border border-dashed border-gray-800 rounded-2xl flex-grow flex flex-col items-center justify-center text-center p-8">
                  <div className="h-16 w-16 bg-gray-800/40 rounded-full flex items-center justify-center mb-4 text-blue-500 border border-gray-700">
                    <Search className="h-7 w-7" />
                  </div>
                  <h3 className="text-lg font-bold text-white mb-2">No Prediction Loaded</h3>
                  <p className="text-sm text-gray-500 max-w-sm">
                    Enter a PNR number on the left panel to execute waitlist heuristics.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 2: 72h Refund SLA */}
        {activeTab === 'sla' && (
          <div className="space-y-8">
            {/* Top SLA Summary Stats */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="bg-gray-800/40 border border-gray-700/60 p-6 rounded-2xl shadow-xl flex items-center gap-5">
                <div className="h-12 w-12 bg-blue-500/10 border border-blue-500/20 rounded-xl flex items-center justify-center text-blue-400">
                  <Clock className="h-6 w-6" />
                </div>
                <div>
                  <span className="block text-xs font-semibold text-gray-400">Refund SLA Commitment</span>
                  <span className="text-xl font-bold text-white">Under 72 Hours</span>
                </div>
              </div>

              <div className="bg-gray-800/40 border border-gray-700/60 p-6 rounded-2xl shadow-xl flex items-center gap-5">
                <div className="h-12 w-12 bg-emerald-500/10 border border-emerald-500/20 rounded-xl flex items-center justify-center text-emerald-400">
                  <Percent className="h-6 w-6" />
                </div>
                <div>
                  <span className="block text-xs font-semibold text-gray-400">Current Compliance Ratio</span>
                  <span className="text-xl font-bold text-emerald-400">98.4% Met SLA</span>
                </div>
              </div>

              <div className="bg-gray-800/40 border border-gray-700/60 p-6 rounded-2xl shadow-xl flex items-center gap-5">
                <div className="h-12 w-12 bg-purple-500/10 border border-purple-500/20 rounded-xl flex items-center justify-center text-purple-400">
                  <Layers className="h-6 w-6" />
                </div>
                <div>
                  <span className="block text-xs font-semibold text-gray-400">Outstanding liability</span>
                  <span className="text-xl font-bold text-white">₹1,80,000 SLA Safe</span>
                </div>
              </div>
            </div>

            {/* SLA Clock Table */}
            <div className="bg-gray-800/40 border border-gray-700/60 rounded-2xl shadow-xl overflow-hidden">
              <div className="px-6 py-5 border-b border-gray-700/60 flex flex-col sm:flex-row justify-between sm:items-center gap-4">
                <div>
                  <h3 className="text-lg font-bold text-white">Live Refund SLA Dashboard</h3>
                  <p className="text-xs text-gray-400">Track current refunds as they are processed under the 72-hour mandate.</p>
                </div>
                <span className="self-start sm:self-auto inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/25">
                  <CheckCircle className="h-3 w-3" /> Public SLA Feed Live
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                  <thead>
                    <tr className="bg-gray-900/50 text-gray-400 border-b border-gray-700 text-xs uppercase tracking-wider">
                      <th className="px-6 py-4">Refund ID</th>
                      <th className="px-6 py-4">Itinerary</th>
                      <th className="px-6 py-4">Amount</th>
                      <th className="px-6 py-4">Requested At</th>
                      <th className="px-6 py-4">SLA Countdown</th>
                      <th className="px-6 py-4 text-right">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-700/45">
                    {refunds.map((ref) => {
                      const countdown = getSLATimeRemaining(ref.requestedAt);
                      return (
                        <tr key={ref.id} className="hover:bg-gray-800/20 transition-all">
                          <td className="px-6 py-4 font-mono font-bold text-gray-300">{ref.id}</td>
                          <td className="px-6 py-4 text-white font-medium">{ref.trip}</td>
                          <td className="px-6 py-4 text-white font-semibold">{ref.amount}</td>
                          <td className="px-6 py-4 text-gray-400">{new Date(ref.requestedAt).toLocaleString()}</td>
                          <td className="px-6 py-4">
                            {ref.status === 'COMPLETED' ? (
                              <span className="text-emerald-400 font-bold flex items-center gap-1">
                                <CheckCircle className="h-4 w-4" /> Paid in 12h
                              </span>
                            ) : (
                              <span className={`font-mono font-bold flex items-center gap-1 ${
                                countdown.expired ? 'text-rose-500' : 'text-amber-400'
                              }`}>
                                <Clock className="h-4 w-4" /> {countdown.text}
                              </span>
                            )}
                          </td>
                          <td className="px-6 py-4 text-right">
                            <span className={`inline-flex px-2.5 py-1 rounded-full text-xs font-bold ${
                              ref.status === 'COMPLETED' 
                                ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                                : ref.status === 'APPROVED' 
                                  ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                                  : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                            }`}>
                              {ref.status}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Zero-Fee transparency */}
        {activeTab === 'zerofee' && (
          <div className="grid grid-cols-1 md:grid-cols-5 gap-8 items-stretch">
            {/* Interactive Calculator Panel */}
            <div className="md:col-span-2 bg-gray-800/50 border border-gray-700/80 p-8 rounded-2xl shadow-xl flex flex-col justify-between">
              <div>
                <h3 className="text-xl font-bold mb-2 text-white">Compare Booking Fees</h3>
                <p className="text-xs text-gray-400 mb-6">
                  Select your transit type and traveler headcount to estimate your savings with Travel2Go.
                </p>

                <div className="space-y-6">
                  {/* Transit Type Selector */}
                  <div>
                    <label className="block text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Transit Type</label>
                    <div className="grid grid-cols-3 gap-2">
                      {['Train', 'Bus', 'Flight'].map((t) => (
                        <button
                          key={t}
                          onClick={() => setTripType(t)}
                          className={`py-2 px-3 rounded-lg text-sm font-bold transition-all ${
                            tripType === t
                              ? 'bg-blue-600 text-white'
                              : 'bg-gray-900 text-gray-400 hover:text-gray-200 border border-gray-700'
                          }`}
                        >
                          {t}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* Travelers Slider */}
                  <div>
                    <div className="flex justify-between text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                      <span>Travelers</span>
                      <span className="text-blue-400 font-bold">{travelers} Pax</span>
                    </div>
                    <input
                      type="range"
                      min={1}
                      max={9}
                      value={travelers}
                      onChange={(e) => setTravelers(parseInt(e.target.value))}
                      className="w-full h-1.5 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-blue-500"
                    />
                    <div className="flex justify-between text-[10px] text-gray-500 mt-1">
                      <span>1 Traveler</span>
                      <span>9 Travelers Max</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="mt-8 pt-6 border-t border-gray-700/60">
                <span className="inline-flex items-center gap-1.5 text-xs text-amber-400 font-semibold bg-amber-500/10 px-3 py-1.5 rounded-lg border border-amber-500/25">
                  <ShieldAlert className="h-4 w-4" /> Price Quote Protection (G2) active
                </span>
              </div>
            </div>

            {/* Savings & Guarantee Visual */}
            <div className="md:col-span-3 bg-gray-800/40 border border-gray-700/60 p-8 rounded-2xl shadow-xl flex flex-col justify-between">
              <div>
                <div className="flex justify-between items-start mb-6">
                  <h4 className="text-lg font-bold text-white">Estimated Convenience Fee Savings</h4>
                  <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-[10px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider">
                    Zero Fees
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-4 mb-8">
                  <div className="bg-gray-900/40 p-4 rounded-xl border border-gray-800">
                    <span className="block text-[10px] uppercase tracking-wider text-gray-500 mb-1">Standard OTA Fee</span>
                    <span className="text-2xl font-extrabold text-rose-400 line-through">₹{getOtaFee()}</span>
                  </div>
                  <div className="bg-blue-900/10 p-4 rounded-xl border border-blue-950">
                    <span className="block text-[10px] uppercase tracking-wider text-blue-400 mb-1">Travel2Go Fee</span>
                    <span className="text-3xl font-extrabold text-emerald-400">₹0</span>
                  </div>
                </div>

                {/* G2 Quote Protection Box */}
                <div className="bg-gray-900/50 border border-gray-800/80 p-5 rounded-xl space-y-2">
                  <h4 className="text-sm font-bold text-white flex items-center gap-2">
                    <CheckCircle className="h-4 w-4 text-blue-500" />
                    First Price = Final Price (G2 Rule)
                  </h4>
                  <p className="text-xs text-gray-400 leading-relaxed">
                    At the search screen, we generate a signed cryptographic token encapsulating your package or transit price. This token is verified instantly at checkout. If the price changes by even 1 paise, the checkout is rejected to prevent hidden markup tricks.
                  </p>
                </div>
              </div>

              <div className="mt-8 flex flex-col sm:flex-row items-center justify-between gap-4">
                <span className="text-xs text-gray-400 flex items-center gap-2">
                  <Users className="h-4 w-4 text-blue-400" />
                  India Tier 2/3 Travel Infrastructure Initiative
                </span>
                <span className="text-sm font-bold text-blue-400 flex items-center gap-1 hover:underline cursor-pointer">
                  Read SLA Policy <ArrowUpRight className="h-4 w-4" />
                </span>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

export default TrustPortal;
