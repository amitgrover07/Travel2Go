import React, { useState, useEffect } from 'react';
import { Mail, Phone, MapPin, Clock, Edit3, X, Save, CheckCircle2, DollarSign, Package } from 'lucide-react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { formatCurrency, cleanHtmlForDisplay } from '../utils/formatUtils';

const BUCKETS = [
  { id: 'NEW', label: 'New', color: 'bg-blue-100 border-blue-300 text-blue-800' },
  { id: 'CONTACTED', label: 'Contacted', color: 'bg-yellow-100 border-yellow-300 text-yellow-800' },
  { id: 'PROPOSAL_SENT', label: 'Proposal Sent', color: 'bg-purple-100 border-purple-300 text-purple-800' },
  { id: 'NEGOTIATION', label: 'Negotiation', color: 'bg-orange-100 border-orange-300 text-orange-800' },
  { id: 'CONVERTED', label: 'Converted', color: 'bg-green-100 border-green-300 text-green-800' },
  { id: 'CLOSED_LOST', label: 'Closed (Lost)', color: 'bg-gray-100 border-gray-300 text-gray-800' },
];

const AdminLeads = () => {
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [draggedLead, setDraggedLead] = useState(null);
  
  // Modal for viewing/editing a lead
  const [selectedLead, setSelectedLead] = useState(null);
  const [editNotes, setEditNotes] = useState('');

  useEffect(() => {
    fetchLeads();
  }, []);

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
      setLeads(leads.map(lead => lead.id === id ? response.data.lead : lead));
      if (selectedLead && selectedLead.id === id) {
        setSelectedLead(response.data.lead);
      }
      toast.success('Lead updated successfully');
    } catch (error) {
      console.error('Error updating lead:', error);
      toast.error('Failed to update lead');
      fetchLeads(); // revert optimistic update
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
      // Optimistic UI update
      const updatedLeads = leads.map(l => 
        l.id === draggedLead.id ? { ...l, status: targetStatus } : l
      );
      setLeads(updatedLeads);
      updateLead(draggedLead.id, { ...draggedLead, status: targetStatus });
    }
    setDraggedLead(null);
  };

  const handleOpenLead = (lead) => {
    setSelectedLead(lead);
    setEditNotes(lead.notes || '');
  };

  const handleSaveNotes = () => {
    if (selectedLead) {
      updateLead(selectedLead.id, { ...selectedLead, notes: editNotes });
    }
  };

  const getLeadsByStatus = (status) => leads.filter(l => (l.status || 'NEW') === status);

  if (loading) {
    return <div className="flex items-center justify-center h-64 text-gray-500">Loading Leads CRM...</div>;
  }

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
                    className="bg-white p-4 rounded-lg border border-gray-200 shadow-sm hover:shadow-md cursor-pointer transition-all hover:border-blue-300"
                  >
                    <div className="flex justify-between items-start mb-2">
                      <h4 className="font-bold text-gray-900 text-sm">{lead.firstName} {lead.lastName}</h4>
                      <span className="text-xs text-gray-400">{new Date(lead.leadDate).toLocaleDateString()}</span>
                    </div>
                    <div className="text-xs text-gray-600 space-y-1 mb-3">
                      <div className="flex items-center gap-1.5 truncate"><Package size={12} className="shrink-0" /> {lead.packageCode}</div>
                      {lead.bestTimeToReach && <div className="flex items-center gap-1.5 truncate text-orange-600 font-medium"><Clock size={12} className="shrink-0"/> {lead.bestTimeToReach}</div>}
                    </div>
                    <div className="flex justify-between items-center pt-2 border-t border-gray-100">
                      <span className="text-xs font-semibold text-gray-800">{formatCurrency(lead.finalPrice)} INR</span>
                      <span className="text-[10px] bg-gray-100 px-1.5 py-0.5 rounded text-gray-500">{lead.source}</span>
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

      {/* Lead Details Modal */}
      {selectedLead && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col animate-in fade-in zoom-in duration-200">
            <div className="px-6 py-4 border-b flex justify-between items-center bg-gray-50">
              <h3 className="text-xl font-bold text-gray-900">Lead Details</h3>
              <button onClick={() => setSelectedLead(null)} className="text-gray-400 hover:text-gray-600 transition-colors">
                <X size={24} />
              </button>
            </div>
            
            <div className="p-6 overflow-y-auto grid grid-cols-1 md:grid-cols-2 gap-8">
              <div className="space-y-6">
                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Customer Information</h4>
                  <div className="bg-gray-50 rounded-xl p-4 space-y-3 text-sm">
                    <div className="flex items-center gap-2"><div className="font-semibold w-20">Name:</div> {selectedLead.firstName} {selectedLead.lastName}</div>
                    <div className="flex items-center gap-2"><div className="font-semibold w-20">Email:</div> <a href={`mailto:${selectedLead.email}`} className="text-blue-600 hover:underline">{selectedLead.email}</a></div>
                    <div className="flex items-center gap-2"><div className="font-semibold w-20">Phone:</div> <a href={`tel:${selectedLead.phone}`} className="text-blue-600 hover:underline">{selectedLead.phone}</a></div>
                    <div className="flex items-center gap-2"><div className="font-semibold w-20">Location:</div> {selectedLead.location}</div>
                    {selectedLead.bestTimeToReach && (
                      <div className="flex items-center gap-2 text-orange-600"><div className="font-semibold w-20">Call Time:</div> {selectedLead.bestTimeToReach}</div>
                    )}
                  </div>
                </div>

                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Package Information</h4>
                  <div className="bg-blue-50 rounded-xl p-4 space-y-3 text-sm text-blue-900">
                    <div className="font-bold text-lg mb-1">{selectedLead.packageTitle} <span className="text-xs font-normal text-blue-700 bg-blue-100 px-2 py-0.5 rounded-full ml-2">{selectedLead.packageCode}</span></div>
                    <div className="flex justify-between items-center pt-2 border-t border-blue-100">
                      <span>Final Price:</span>
                      <span className="font-bold text-lg">{formatCurrency(selectedLead.finalPrice)} INR</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-6">
                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Status Management</h4>
                  <select 
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-semibold"
                    value={selectedLead.status || 'NEW'}
                    onChange={(e) => updateLead(selectedLead.id, { ...selectedLead, status: e.target.value })}
                  >
                    {BUCKETS.map(b => <option key={b.id} value={b.id}>{b.label}</option>)}
                  </select>
                </div>

                <div>
                  <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider mb-3">Admin Notes</h4>
                  <textarea 
                    className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 h-40 resize-none text-sm"
                    value={editNotes}
                    onChange={(e) => setEditNotes(e.target.value)}
                    placeholder="Record your calls, follow-ups, and requirements here..."
                  ></textarea>
                  <button 
                    onClick={handleSaveNotes}
                    className="mt-3 w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 rounded-lg transition-colors flex justify-center items-center gap-2"
                  >
                    <Save size={18} /> Save Notes
                  </button>
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
