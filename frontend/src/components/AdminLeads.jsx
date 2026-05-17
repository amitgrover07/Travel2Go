import React, { useState, useEffect } from 'react';
import { Mail, Phone, MapPin, Clock, Edit3, X, Save, CheckCircle2, DollarSign, Package, History, Send, Search } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { formatCurrency } from '../utils/formatUtils';

const BUCKETS = [
  { id: 'NEW', label: 'New', color: 'bg-blue-100 border-blue-300 text-blue-800' },
  { id: 'CONTACTED', label: 'Contacted', color: 'bg-yellow-100 border-yellow-300 text-yellow-800' },
  { id: 'PROPOSAL_SENT', label: 'Proposal Sent', color: 'bg-purple-100 border-purple-300 text-purple-800' },
  { id: 'NEGOTIATION', label: 'Negotiation', color: 'bg-orange-100 border-orange-300 text-orange-800' },
  { id: 'CONVERTED', label: 'Converted', color: 'bg-green-100 border-green-300 text-green-800' },
  { id: 'CLOSED_LOST', label: 'Closed (Lost)', color: 'bg-gray-100 border-gray-300 text-gray-800' },
];

const AdminLeads = ({ packages = [], customPackages = [] }) => {
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [draggedLead, setDraggedLead] = useState(null);
  
  // Drag Confirmation
  const [confirmMove, setConfirmMove] = useState(null);

  // Modal for viewing/editing a lead
  const [selectedLead, setSelectedLead] = useState(null);
  const [editNotes, setEditNotes] = useState('');
  
  // Edit Customer Info
  const [isEditingDetails, setIsEditingDetails] = useState(false);
  const [editDetails, setEditDetails] = useState({});

  // Send Email State
  const [selectedPackageId, setSelectedPackageId] = useState('');
  const [sendingEmail, setSendingEmail] = useState(false);
  const [packageSearchQuery, setPackageSearchQuery] = useState('');
  const [showPackageDropdown, setShowPackageDropdown] = useState(false);

  useEffect(() => {
    fetchLeads();
  }, []);

  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (showPackageDropdown && !e.target.closest('.searchable-package-dropdown')) {
        setShowPackageDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, [showPackageDropdown]);

  const fetchLeads = async () => {
    setLoading(true);
    try {
      const response = await api.get('/bookings/leads');
      setLeads(response.data || []);
    } catch (error) {
      console.error('Error fetching leads:', error);
      toast.error('Failed to load leads');
    } finally {
      setLoading(false);
    }
  };

  const updateLead = async (id, updates) => {
    try {
      const response = await api.put(`/bookings/leads/${id}`, updates);
      setLeads(leads.map(lead => lead.id === id ? response.data : lead));
      if (selectedLead && selectedLead.id === id) {
        setSelectedLead(response.data);
      }
      toast.success('Lead updated successfully');
      return response.data;
    } catch (error) {
      console.error('Error updating lead:', error);
      toast.error('Failed to update lead');
      fetchLeads(); // revert optimistic update
      throw error;
    }
  };

  const handleDragStart = (e, lead) => {
    setDraggedLead(lead);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  };

  const handleDrop = (e, targetStatus) => {
    e.preventDefault();
    if (draggedLead && draggedLead.status !== targetStatus) {
      setConfirmMove({ lead: draggedLead, targetStatus });
    }
    setDraggedLead(null);
  };

  const confirmDragDrop = async () => {
    if (!confirmMove) return;
    const { lead, targetStatus } = confirmMove;
    const updatedLeads = leads.map(l => 
      l.id === lead.id ? { ...l, status: targetStatus } : l
    );
    setLeads(updatedLeads);
    setConfirmMove(null);
    try {
      await updateLead(lead.id, { ...lead, status: targetStatus });
    } catch (err) {
      // Revert is handled inside updateLead
    }
  };

  const handleOpenLead = (lead) => {
    setSelectedLead(lead);
    setEditNotes(lead.notes || '');
    setIsEditingDetails(false);
    setEditDetails({
      firstName: lead.firstName || '',
      lastName: lead.lastName || '',
      email: lead.email || '',
      phone: lead.phone || '',
      location: lead.location || '',
      bestTimeToReach: lead.bestTimeToReach || ''
    });
    setSelectedPackageId('');
    setPackageSearchQuery('');
    setShowPackageDropdown(false);
  };

  const handleSaveNotes = () => {
    if (selectedLead) {
      updateLead(selectedLead.id, { ...selectedLead, notes: editNotes });
    }
  };

  const handleSaveDetails = async () => {
    if (selectedLead) {
      await updateLead(selectedLead.id, { ...selectedLead, ...editDetails });
      setIsEditingDetails(false);
    }
  };

  const handleSendEmail = async () => {
    if (!selectedPackageId) {
      toast.error('Please select a package to send');
      return;
    }

    setSendingEmail(true);
    try {
      const pkg = packages.find(p => p.id === selectedPackageId) || customPackages.find(p => p.id === selectedPackageId);
      if (!pkg) throw new Error("Package not found");
      const isCustom = customPackages.some(p => p.id === selectedPackageId);

      const requestPayload = {
        firstName: selectedLead.firstName,
        lastName: selectedLead.lastName,
        email: selectedLead.email,
        phone: selectedLead.phone,
        location: selectedLead.location,
        packageId: pkg.id,
        packageTitle: pkg.title,
        isCustom: isCustom,
        leadId: selectedLead.id
      };

      await api.post('/bookings', requestPayload);
      toast.success('Package sent successfully via Email!');
      // Refresh leads to get updated audit log and mail count
      await fetchLeads();
      // Update selected lead to reflect changes if modal is open
      const refreshedLead = (await api.get('/bookings/leads')).data.find(l => l.id === selectedLead.id);
      if (refreshedLead) setSelectedLead(refreshedLead);
      setSelectedPackageId('');
      setPackageSearchQuery('');
      setShowPackageDropdown(false);
    } catch (error) {
      toast.error('Failed to send email. Check console.');
      console.error(error);
    } finally {
      setSendingEmail(false);
    }
  };

  const getLeadsByStatus = (status) => leads.filter(l => (l.status || 'NEW') === status);

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-500">Loading Leads CRM...</div>;
  }

  const allAvailablePackages = [...packages, ...customPackages];
  const filteredPackages = allAvailablePackages.filter(pkg => 
    pkg.title.toLowerCase().includes(packageSearchQuery.toLowerCase()) || 
    pkg.packageCode.toLowerCase().includes(packageSearchQuery.toLowerCase())
  );

  return (
    <div className="w-full">
      <div className="flex items-center justify-between mb-6 border-b pb-4">
        <h2 className="text-2xl font-bold text-gray-900 flex items-center">
          <DollarSign className="mr-2 text-blue-600" /> Leads CRM
        </h2>
        <div className="text-sm text-gray-500">Total Leads: {leads.length}</div>
      </div>

      <div className="flex gap-4 overflow-x-auto pb-6 h-[calc(100vh-200px)] items-start">
        {BUCKETS.map(bucket => {
          const bucketLeads = getLeadsByStatus(bucket.id);
          return (
            <div 
              key={bucket.id}
              className={`flex-shrink-0 w-80 bg-gray-50 rounded-xl border border-gray-200 shadow-sm flex flex-col h-full`}
              onDragOver={handleDragOver}
              onDrop={(e) => handleDrop(e, bucket.id)}
            >
              <div className={`px-4 py-3 rounded-t-xl border-b font-bold flex justify-between items-center ${bucket.color}`}>
                <span>{bucket.label}</span>
                <span className="bg-white/50 px-2 py-0.5 rounded-full text-xs font-black">{bucketLeads.length}</span>
              </div>
              
              <div className="flex-1 overflow-y-auto p-3 space-y-3">
                {bucketLeads.map(lead => (
                  <div 
                    key={lead.id}
                    draggable
                    onDragStart={(e) => handleDragStart(e, lead)}
                    onClick={() => handleOpenLead(lead)}
                    className="bg-white p-4 rounded-lg border border-gray-200 shadow-sm hover:shadow-md cursor-pointer transition-all hover:border-blue-300 relative"
                  >
                    <div className="flex justify-between items-start mb-2 border-b border-gray-100 pb-2">
                      <span className="text-[10px] font-bold bg-gray-100 text-gray-600 px-2 py-0.5 rounded uppercase tracking-wider">{lead.leadIdentifier || 'LEAD'}</span>
                      <div className="flex items-center text-[10px] bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded font-bold" title="Emails Sent">
                        <Mail size={10} className="mr-1"/> {lead.mailSentCount || 1}
                      </div>
                    </div>
                    <div className="flex justify-between items-start mb-2">
                      <h4 className="font-bold text-gray-900 text-sm">{lead.firstName} {lead.lastName}</h4>
                      <span className="text-xs text-gray-400 shrink-0">{new Date(lead.leadDate).toLocaleDateString()}</span>
                    </div>
                    <div className="text-xs text-gray-600 space-y-1 mb-3">
                      <div className="flex items-center gap-1.5 truncate"><Package size={12} className="shrink-0 text-gray-400" /> {lead.packageCode}</div>
                      {lead.bestTimeToReach && <div className="flex items-center gap-1.5 truncate text-orange-600 font-medium"><Clock size={12} className="shrink-0"/> {lead.bestTimeToReach}</div>}
                    </div>
                    <div className="flex justify-between items-center pt-2 border-t border-gray-100">
                      <span className="text-xs font-semibold text-gray-800">{formatCurrency(lead.finalPrice)}</span>
                      <span className="text-[10px] text-gray-400">{lead.source}</span>
                    </div>
                  </div>
                ))}
                {bucketLeads.length === 0 && (
                  <div className="text-center text-xs text-gray-400 py-8 italic border-2 border-dashed border-gray-200 rounded-lg">
                    Drag leads here
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Drag Confirmation Modal */}
      {confirmMove && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm p-6 text-center animate-in zoom-in duration-200">
            <h3 className="text-lg font-bold text-gray-900 mb-2">Move Lead</h3>
            <p className="text-sm text-gray-600 mb-6">Are you sure you want to move <strong>{confirmMove.lead.firstName} {confirmMove.lead.lastName}</strong> to <strong>{BUCKETS.find(b=>b.id===confirmMove.targetStatus)?.label}</strong>?</p>
            <div className="flex justify-center gap-3">
              <button onClick={() => setConfirmMove(null)} className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg font-medium hover:bg-gray-200 transition-colors">Cancel</button>
              <button onClick={confirmDragDrop} className="px-4 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors">Confirm Move</button>
            </div>
          </div>
        </div>
      )}

      {/* Lead Details Modal */}
      {selectedLead && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-5xl h-[90vh] overflow-hidden flex flex-col animate-in fade-in zoom-in duration-200">
            <div className="px-6 py-4 border-b flex justify-between items-center bg-gray-50 shrink-0">
              <div className="flex items-center gap-3">
                <h3 className="text-xl font-bold text-gray-900">Lead Details</h3>
                <span className="bg-blue-100 text-blue-800 text-xs font-bold px-2 py-1 rounded">{selectedLead.leadIdentifier || 'LEAD'}</span>
              </div>
              <button onClick={() => setSelectedLead(null)} className="text-gray-400 hover:text-gray-600 transition-colors">
                <X size={24} />
              </button>
            </div>
            
            <div className="p-6 overflow-y-auto flex-1 grid grid-cols-1 lg:grid-cols-3 gap-8">
              {/* Left Column */}
              <div className="space-y-6 lg:col-span-1">
                <div>
                  <div className="flex justify-between items-center mb-3">
                    <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider">Customer Information</h4>
                    {!isEditingDetails ? (
                      <button onClick={() => setIsEditingDetails(true)} className="text-xs text-blue-600 hover:underline flex items-center"><Edit3 size={12} className="mr-1"/> Edit</button>
                    ) : (
                      <button onClick={() => setIsEditingDetails(false)} className="text-xs text-gray-500 hover:underline">Cancel</button>
                    )}
                  </div>
                  
                  <div className="bg-gray-50 rounded-xl p-4 text-sm">
                    {!isEditingDetails ? (
                      <div className="space-y-3">
                        <div className="flex items-start gap-2"><div className="font-semibold w-20 shrink-0 text-gray-500">Name:</div> <div className="font-medium">{selectedLead.firstName} {selectedLead.lastName}</div></div>
                        <div className="flex items-start gap-2"><div className="font-semibold w-20 shrink-0 text-gray-500">Email:</div> <a href={`mailto:${selectedLead.email}`} className="text-blue-600 hover:underline break-all">{selectedLead.email}</a></div>
                        <div className="flex items-start gap-2"><div className="font-semibold w-20 shrink-0 text-gray-500">Phone:</div> <a href={`tel:${selectedLead.phone}`} className="text-blue-600 hover:underline">{selectedLead.phone}</a></div>
                        <div className="flex items-start gap-2"><div className="font-semibold w-20 shrink-0 text-gray-500">Location:</div> <div>{selectedLead.location}</div></div>
                        <div className="flex items-start gap-2 text-orange-600"><div className="font-semibold w-20 shrink-0">Call Time:</div> <div>{selectedLead.bestTimeToReach || 'Not specified'}</div></div>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        <div className="flex gap-2">
                          <input type="text" value={editDetails.firstName} onChange={e=>setEditDetails({...editDetails, firstName: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="First Name" />
                          <input type="text" value={editDetails.lastName} onChange={e=>setEditDetails({...editDetails, lastName: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="Last Name" />
                        </div>
                        <input type="email" value={editDetails.email} onChange={e=>setEditDetails({...editDetails, email: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="Email" />
                        <input type="text" value={editDetails.phone} onChange={e=>setEditDetails({...editDetails, phone: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="Phone" />
                        <input type="text" value={editDetails.location} onChange={e=>setEditDetails({...editDetails, location: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="Location" />
                        <input type="text" value={editDetails.bestTimeToReach} onChange={e=>setEditDetails({...editDetails, bestTimeToReach: e.target.value})} className="w-full px-2 py-1 border rounded text-sm" placeholder="Best Time to Reach" />
                        <button onClick={handleSaveDetails} className="w-full bg-blue-600 text-white font-medium py-1.5 rounded text-sm hover:bg-blue-700">Save Details</button>
                      </div>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Package Information</h4>
                  <div className="bg-blue-50 rounded-xl p-4 space-y-3 text-sm text-blue-900 border border-blue-100">
                    <div className="font-bold text-base mb-1">{selectedLead.packageTitle} </div>
                    <div className="text-xs font-medium text-blue-700 mb-2">Code: {selectedLead.packageCode}</div>
                    <div className="flex justify-between items-center pt-2 border-t border-blue-200/50">
                      <span>Final Price:</span>
                      <span className="font-bold text-base">{formatCurrency(selectedLead.finalPrice)} INR</span>
                    </div>
                  </div>
                </div>

                {/* Send Email Section */}
                {selectedLead.status !== 'NEW' && (
                  <div>
                    <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Send Email Package</h4>
                    <div className="bg-purple-50 rounded-xl p-4 space-y-3 border border-purple-100">
                      <p className="text-xs text-purple-800">Send another itinerary directly to this lead. It will increment the mail count.</p>
                      
                      {/* Searchable Dropdown */}
                      <div className="searchable-package-dropdown relative">
                        <div className="relative">
                          <input 
                            type="text"
                            className="w-full px-3 py-2 border border-purple-200 rounded text-sm focus:ring-2 focus:ring-purple-500 bg-white pr-8 font-medium"
                            placeholder="Search package code or title..."
                            value={packageSearchQuery}
                            onChange={(e) => {
                              setPackageSearchQuery(e.target.value);
                              setShowPackageDropdown(true);
                            }}
                            onFocus={() => setShowPackageDropdown(true)}
                          />
                          <Search size={14} className="absolute right-2.5 top-3 text-purple-400 pointer-events-none" />
                        </div>
                        
                        {showPackageDropdown && (
                          <div className="absolute z-50 w-full mt-1 bg-white border border-purple-100 rounded-lg shadow-lg max-h-60 overflow-y-auto">
                            {filteredPackages.length > 0 ? (
                              filteredPackages.map(pkg => (
                                <button
                                  key={pkg.id}
                                  type="button"
                                  className="w-full text-left px-3 py-2 text-xs hover:bg-purple-50 text-gray-700 flex justify-between items-center border-b border-gray-50 last:border-0"
                                  onClick={() => {
                                    setSelectedPackageId(pkg.id);
                                    setPackageSearchQuery(`[${pkg.packageCode}] ${pkg.title}`);
                                    setShowPackageDropdown(false);
                                  }}
                                >
                                  <span className="font-semibold truncate mr-2">{pkg.title}</span>
                                  <span className="text-[9px] text-purple-600 bg-purple-50 px-1.5 py-0.5 rounded font-black shrink-0">{pkg.packageCode}</span>
                                </button>
                              ))
                            ) : (
                              <div className="p-3 text-center text-xs text-gray-400 italic">No packages found</div>
                            )}
                          </div>
                        )}
                      </div>

                      {/* Selected Package Preview Card */}
                      {selectedPackageId && (() => {
                        const pkg = allAvailablePackages.find(p => p.id === selectedPackageId);
                        if (!pkg) return null;
                        return (
                          <div className="bg-white border border-purple-100 rounded-xl p-3 space-y-2 shadow-sm text-xs mt-2 relative animate-in fade-in zoom-in-95 duration-200">
                            <div className="flex justify-between items-start">
                              <h5 className="font-bold text-gray-900 pr-4">{pkg.title}</h5>
                              <span className="bg-purple-100 text-purple-800 text-[10px] font-bold px-1.5 py-0.5 rounded shrink-0">{pkg.packageCode}</span>
                            </div>
                            <div className="grid grid-cols-2 gap-1 text-[10px] text-gray-600 border-t border-purple-50 pt-2">
                              <div><span className="font-semibold text-gray-400">Dest:</span> {pkg.destination || 'N/A'}</div>
                              <div><span className="font-semibold text-gray-400">Duration:</span> {pkg.duration?.days ? `${pkg.duration.days}D / ${pkg.duration.nights}N` : 'Custom'}</div>
                            </div>
                            <div className="flex justify-between items-center border-t border-purple-50 pt-1.5 text-[10px]">
                              <span className="font-semibold text-gray-500 font-medium">Final Price:</span>
                              <span className="font-bold text-purple-700 text-[11px]">{formatCurrency(pkg.pricing?.finalPrice || 0)} INR</span>
                            </div>
                          </div>
                        );
                      })()}

                      <button 
                        onClick={handleSendEmail}
                        disabled={sendingEmail || !selectedPackageId}
                        className="w-full bg-purple-600 hover:bg-purple-700 disabled:bg-purple-300 text-white font-medium py-2 rounded transition-colors flex justify-center items-center gap-2 text-sm"
                      >
                        {sendingEmail ? 'Sending...' : <><Send size={14} /> Send Email</>}
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {/* Middle Column */}
              <div className="space-y-6 lg:col-span-1">
                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Status Management</h4>
                  <select 
                    className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-blue-500 font-semibold shadow-sm bg-white"
                    value={selectedLead.status || 'NEW'}
                    onChange={(e) => updateLead(selectedLead.id, { ...selectedLead, status: e.target.value })}
                  >
                    {BUCKETS.map(b => <option key={b.id} value={b.id}>{b.label}</option>)}
                  </select>
                </div>

                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Admin Notes</h4>
                  <textarea 
                    className="w-full px-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-blue-500 h-64 resize-none text-sm shadow-sm bg-white"
                    value={editNotes}
                    onChange={(e) => setEditNotes(e.target.value)}
                    placeholder="Record your calls, follow-ups, and requirements here..."
                  ></textarea>
                  <button 
                    onClick={handleSaveNotes}
                    className="mt-3 w-full bg-gray-900 hover:bg-black text-white font-bold py-3 rounded-xl transition-colors flex justify-center items-center gap-2 shadow-sm"
                  >
                    <Save size={18} /> Save Notes
                  </button>
                </div>
              </div>

              {/* Right Column: Audit Trail */}
              <div className="space-y-4 lg:col-span-1 flex flex-col h-full">
                <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-1">Audit Trail</h4>
                <div className="bg-gray-50 rounded-xl p-4 flex-1 overflow-y-auto border border-gray-200">
                  {(!selectedLead.auditLogs || selectedLead.auditLogs.length === 0) ? (
                    <div className="text-sm text-gray-400 italic text-center py-8">No history recorded</div>
                  ) : (
                    <div className="relative pl-6 border-l-2 border-gray-200 ml-3 space-y-5">
                      {selectedLead.auditLogs.slice().reverse().map((log, idx) => (
                        <div key={idx} className="relative">
                          {/* Timeline dot/icon */}
                          <div className="absolute -left-[35px] top-1 flex items-center justify-center w-5 h-5 rounded-full border-2 border-white bg-blue-500 text-white shadow shrink-0 z-10">
                            <History size={10} />
                          </div>
                          {/* Card */}
                          <div className="bg-white p-3 rounded-lg border border-gray-200 shadow-sm hover:shadow transition-shadow">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1 mb-2 pb-1.5 border-b border-gray-100">
                              <span className="font-bold text-gray-800 text-[10px] uppercase tracking-wider bg-gray-50 px-1.5 py-0.5 rounded shrink-0 w-max">{log.action.replace('_', ' ')}</span>
                              <span className="text-[9px] text-gray-400 font-medium whitespace-nowrap">{new Date(log.timestamp).toLocaleDateString()} {new Date(log.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}</span>
                            </div>
                            <div className="text-xs text-gray-600 leading-snug mb-2 font-normal">{log.details}</div>
                            <div className="text-[10px] text-gray-400 font-semibold border-t border-gray-50 pt-1.5 flex items-center justify-between">
                              <span>By: <span className="text-gray-600">{log.adminName}</span></span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
              
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminLeads;
