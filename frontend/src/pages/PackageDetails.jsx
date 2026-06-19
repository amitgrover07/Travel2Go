import React, { useState, useEffect } from 'react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { 
  MapPin, Clock, DollarSign, ChevronLeft, ChevronRight, CheckCircle2, XCircle, 
  Calendar, ArrowLeft, X, Loader2, Mail, User, Phone, MapPinIcon, Send,
  Hotel, Car, Compass, Activity, Plane, Ship, Train, Camera, Coffee, 
  Utensils, Tent, Ticket, Shield, HelpCircle, Settings,
  Trash2, Plus, Users, Info, UserPlus
} from 'lucide-react';

const ICON_MAP = {
  Hotel,
  Car,
  Compass,
  MapPin,
  Activity,
  Plane,
  Ship,
  Train,
  Camera,
  Coffee,
  Utensils,
  Tent,
  Ticket,
  Shield,
  HelpCircle
};
import toast from 'react-hot-toast';
import api from '../services/api';
import SEO from '../components/SEO';
import { formatCurrency, numberToWords, isHtmlEmpty, cleanHtmlForDisplay } from '../utils/formatUtils';
import { PackageDetailsSkeleton } from '../components/SkeletonLoader';

const renderBulletPoints = (text) => {
  if (!text) return null;
  const points = text.split('\n').filter(p => p.trim() !== '');
  if (points.length <= 1 && !text.includes('\n')) return text;
  
  return (
    <ul className="list-disc ml-5 space-y-2 mt-2">
      {points.map((point, index) => (
        <li key={index} className="text-gray-700 leading-relaxed">{point}</li>
      ))}
    </ul>
  );
};

const defaultFallbackRule = {
  ruleCode: 'DEFAULT',
  title: 'Default Fallback Rule',
  baseRoomRate: 3000,
  extraAdultRate: 1200,
  cwbRate: 800,
  cnbRate: 400,
  sightseeingTicketPrice: 500,
  vehicles: [
    { vehicleName: 'Sedan', maxPax: 4, dailyRate: 2000, tollCharges: 500, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'SUV', maxPax: 6, dailyRate: 2500, tollCharges: 600, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Innova Crysta', maxPax: 6, dailyRate: 3000, tollCharges: 800, permitTax: 0, driverAllowance: 300 },
    { vehicleName: 'Tempo Traveller', maxPax: 12, dailyRate: 5000, tollCharges: 1500, permitTax: 500, driverAllowance: 500 }
  ]
};

const classifyGuests = (guestsList) => {
  let A = 0;
  let CWB = 0;
  let CNB = 0;
  let I = 0;
  
  guestsList.forEach(g => {
    const age = parseInt(g.age);
    if (isNaN(age)) return;
    
    if (age >= 12) {
      A++;
    } else if (age >= 5) {
      if (g.bedRequired) {
        CWB++;
      } else {
        CNB++;
      }
    } else {
      I++;
    }
  });
  
  return { A, CWB, CNB, I };
};

const allocateRooms = (A, CWB, CNB, I, overrideTriple) => {
  let rooms = [];
  
  if (A === 0) return [];
  
  if (A === 1) {
    if (CWB === 0 && CNB === 0) {
      rooms.push({ tag: "Single Occupancy", adults: 1, cwb: 0, cnb: 0, infants: I });
    } else if (CWB + CNB === 1) {
      rooms.push({ 
        tag: CWB === 1 ? "Double + 1 Extra Bed (CWB)" : "Double + CNB", 
        adults: 1, 
        cwb: CWB, 
        cnb: CNB, 
        infants: I 
      });
    } else {
      let remainingCWB = CWB;
      let remainingCNB = CNB;
      
      let cwbInRoom = Math.min(remainingCWB, 2);
      remainingCWB -= cwbInRoom;
      let cnbInRoom = 0;
      if (cwbInRoom < 2) {
        cnbInRoom = Math.min(remainingCNB, 2 - cwbInRoom);
        remainingCNB -= cnbInRoom;
      }
      rooms.push({
        tag: cwbInRoom > 0 ? "Double + 1 Extra Bed (CWB)" : "Double + CNB",
        adults: 1,
        cwb: cwbInRoom,
        cnb: cnbInRoom,
        infants: I
      });
      
      while (remainingCWB > 0 || remainingCNB > 0) {
        let roomCWB = Math.min(remainingCWB, 3);
        remainingCWB -= roomCWB;
        let roomCNB = 0;
        if (roomCWB < 3) {
          roomCNB = Math.min(remainingCNB, 3 - roomCWB);
          remainingCNB -= roomCNB;
        }
        rooms.push({ tag: "Family Room", adults: 0, cwb: roomCWB, cnb: roomCNB, infants: 0 });
      }
    }
  } else if (A === 2) {
    if (CWB === 0 && CNB === 0) {
      rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: I });
    } else if (CWB + CNB === 1) {
      rooms.push({ 
        tag: CWB === 1 ? "Double + 1 Extra Bed (CWB)" : "Double + CNB", 
        adults: 2, 
        cwb: CWB, 
        cnb: CNB, 
        infants: I 
      });
    } else if (CWB + CNB === 2) {
      rooms.push({ tag: "Single Occupancy", adults: 1, cwb: 0, cnb: 0, infants: 0 });
      rooms.push({ tag: "Single Occupancy", adults: 1, cwb: CWB, cnb: CNB, infants: I });
    } else {
      rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: 0 });
      let remainingCWB = CWB;
      let remainingCNB = CNB;
      while (remainingCWB > 0 || remainingCNB > 0) {
        let roomCWB = Math.min(remainingCWB, 3);
        remainingCWB -= roomCWB;
        let roomCNB = 0;
        if (roomCWB < 3) {
          roomCNB = Math.min(remainingCNB, 3 - roomCWB);
          remainingCNB -= roomCNB;
        }
        rooms.push({ tag: "Family Room", adults: 0, cwb: roomCWB, cnb: roomCNB, infants: I });
      }
    }
  } else if (A === 3) {
    if (CWB === 0 && CNB === 0) {
      if (overrideTriple) {
        rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: 0 });
        rooms.push({ tag: "Single Occupancy", adults: 1, cwb: 0, cnb: 0, infants: 0 });
      } else {
        rooms.push({ tag: "Triple Sharing", adults: 3, cwb: 0, cnb: 0, infants: 0 });
      }
    } else {
      if (overrideTriple) {
        rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: 0 });
        rooms.push({ tag: "Single Occupancy", adults: 1, cwb: CWB, cnb: CNB, infants: I });
      } else {
        rooms.push({ tag: "Triple Sharing", adults: 3, cwb: 0, cnb: 0, infants: 0 });
        rooms.push({ tag: "Single Occupancy", adults: 0, cwb: CWB, cnb: CNB, infants: I });
      }
    }
  } else {
    let remainingAdults = A;
    let remainingCWB = CWB;
    let remainingCNB = CNB;
    
    while (remainingAdults > 0) {
      if (remainingAdults === 3) {
        if (overrideTriple) {
          rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: 0 });
          rooms.push({ tag: "Single Occupancy", adults: 1, cwb: 0, cnb: 0, infants: 0 });
        } else {
          rooms.push({ tag: "Triple Sharing", adults: 3, cwb: 0, cnb: 0, infants: 0 });
        }
        remainingAdults = 0;
      } else if (remainingAdults === 1) {
        rooms.push({ tag: "Single Occupancy", adults: 1, cwb: 0, cnb: 0, infants: 0 });
        remainingAdults = 0;
      } else {
        rooms.push({ tag: "Double Sharing", adults: 2, cwb: 0, cnb: 0, infants: 0 });
        remainingAdults -= 2;
      }
    }
    
    if (I > 0 && rooms.length > 0) {
      rooms[0].infants = I;
    }
    
    let roomIdx = 0;
    while (remainingCWB > 0 || remainingCNB > 0) {
      if (roomIdx < rooms.length) {
        let room = rooms[roomIdx];
        if (room.tag === "Double Sharing" || room.tag === "Single Occupancy") {
          let currentPax = room.adults + (room.cwb || 0) + (room.cnb || 0);
          if (currentPax < 4) {
            let space = 4 - currentPax;
            let addCWB = Math.min(remainingCWB, space);
            room.cwb = (room.cwb || 0) + addCWB;
            remainingCWB -= addCWB;
            space -= addCWB;
            
            if (space > 0) {
              let addCNB = Math.min(remainingCNB, space);
              room.cnb = (room.cnb || 0) + addCNB;
              remainingCNB -= addCNB;
            }
            
            if (room.cwb > 0) {
              room.tag = "Double + 1 Extra Bed (CWB)";
            } else if (room.cnb > 0) {
              room.tag = "Double + CNB";
            }
          }
        }
        roomIdx++;
      } else {
        let addCWB = Math.min(remainingCWB, 3);
        remainingCWB -= addCWB;
        let addCNB = 0;
        if (addCWB < 3) {
          addCNB = Math.min(remainingCNB, 3 - addCWB);
          remainingCNB -= addCNB;
        }
        rooms.push({ tag: "Family Room", adults: 0, cwb: addCWB, cnb: addCNB, infants: 0 });
      }
    }
  }
  
  return rooms;
};

const recommendVehicle = (totalPax, vehiclesList) => {
  if (!vehiclesList || vehiclesList.length === 0) return null;
  const sorted = [...vehiclesList].sort((a, b) => a.maxPax - b.maxPax);
  const match = sorted.find(v => v.maxPax >= totalPax);
  return match || sorted[sorted.length - 1];
};

const calculateCustomQuote = (rooms, rule, nights, totalPax, days, selectedVehicle) => {
  const baseRoomRate = rule.baseRoomRate;
  const extraAdultRate = rule.extraAdultRate;
  const cwbRate = rule.cwbRate;
  const cnbRate = rule.cnbRate;
  const ticketRate = rule.sightseeingTicketPrice;
  
  let hotelCost = 0;
  rooms.forEach(r => {
    let roomCost = baseRoomRate;
    if (r.adults === 3) {
      roomCost += extraAdultRate;
    }
    if (r.cwb > 0) {
      roomCost += r.cwb * cwbRate;
    }
    if (r.cnb > 0) {
      roomCost += r.cnb * cnbRate;
    }
    hotelCost += roomCost * nights;
  });
  
  let sightseeingCost = totalPax * ticketRate;
  
  let transportCost = 0;
  if (selectedVehicle) {
    transportCost = (selectedVehicle.dailyRate * days) + 
                    (selectedVehicle.driverAllowance * days) + 
                    selectedVehicle.tollCharges + 
                    selectedVehicle.permitTax;
  }
  
  const totalCost = hotelCost + sightseeingCost + transportCost;
  
  return {
    hotelCost,
    sightseeingCost,
    transportCost,
    totalCost
  };
};

const PackageDetails = () => {
  const { id } = useParams();
  const [pkg, setPkg] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);
  const [globalTerms, setGlobalTerms] = useState('');
  const [attachedConfigs, setAttachedConfigs] = useState([]);
  const [categories, setCategories] = useState([]);
  const navigate = useNavigate();
  const location = useLocation();

  // Booking form state
  const [showBookingModal, setShowBookingModal] = useState(false);
  const [bookingForm, setBookingForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    location: '',
    bestTimeToReach: ''
  });
  const [bookingLoading, setBookingLoading] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);

  const [allocationRule, setAllocationRule] = useState(null);
  const [guests, setGuests] = useState([
    { name: 'Traveler 1', age: 30, bedRequired: false },
    { name: 'Traveler 2', age: 28, bedRequired: false }
  ]);
  const [overrideTriple, setOverrideTriple] = useState(false);
  const [vehicleOverride, setVehicleOverride] = useState('');

  const nights = pkg?.duration?.nights || 1;
  const days = pkg?.duration?.days || 1;
  
  // Guest classifications
  const { A, CWB, CNB, I } = classifyGuests(guests);
  const totalPax = A + CWB + CNB; // headcount for sightseeing tickets & vehicle recommendation (exclude infant)
  
  // Room allocation
  const allocatedRooms = allocateRooms(A, CWB, CNB, I, overrideTriple);
  
  // Vehicle lists & selection
  const vehicles = allocationRule?.vehicles || [];
  const recommendedVehicle = recommendVehicle(totalPax, vehicles);
  const selectedVehicle = vehicleOverride 
    ? (vehicles.find(v => v.vehicleName === vehicleOverride) || recommendedVehicle)
    : recommendedVehicle;
  
  // Pricing breakdown
  const quote = allocationRule 
    ? calculateCustomQuote(allocatedRooms, allocationRule, nights, totalPax, days, selectedVehicle)
    : null;
  
  const perAdultCost = (quote && A > 0) ? Math.round(quote.totalCost / A) : 0;

  // Admin Send to Customer state
  const [showSendModal, setShowSendModal] = useState(false);
  const [sendLoading, setSendLoading] = useState(false);
  const [sendForm, setSendForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    location: '',
    bestTimeToReach: ''
  });

  const getTokenPayload = () => {
    const token = localStorage.getItem('token');
    if (!token) return null;
    try {
      const base64Url = token.split('.')[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  };

  useEffect(() => {
    const payload = getTokenPayload();
    setIsAdmin(payload?.role === 'ADMIN');
  }, []);

  useEffect(() => {
    const fetchPackageDetails = async () => {
      try {
        const response = await api.get(`/packages/${id}`);
        const pkgData = response.data;
        setPkg(pkgData);
        
        // Fetch attached configurations and categories
        const [configsRes, catsRes] = await Promise.all([
          api.get(`/configurators/attached?packageId=${pkgData.id}&destination=${pkgData.destination}`),
          api.get('/configurator-categories')
        ]);
        setAttachedConfigs(Array.isArray(configsRes.data) ? configsRes.data : []);
        setCategories(Array.isArray(catsRes.data) ? catsRes.data : []);

        try {
          const ruleRes = await api.get(`/allocation-rules/package/${pkgData.id}`);
          setAllocationRule(ruleRes.data);
        } catch (ruleErr) {
          console.log('No specific allocation rule found for package, using fallback rates', ruleErr);
          setAllocationRule(defaultFallbackRule);
        }
      } catch (error) {
        console.error('Error fetching package details:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchPackageDetails();
    fetchGlobalTerms();
  }, [id]);

  const fetchGlobalTerms = async () => {
    try {
      const response = await api.get('/settings/terms');
      setGlobalTerms(response.data.termsAndConditions || '');
    } catch (error) {
      console.error('Error fetching global terms:', error);
    }
  };

  const handleBookNow = async () => {
    const token = localStorage.getItem('token');
    
    // Check if token exists and is not expired
    let isValidToken = false;
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        // Add 1 minute buffer for expiration
        if (payload.exp && (payload.exp * 1000) > (Date.now() + 60000)) {
          isValidToken = true;
        }
      } catch (e) {
        // Invalid token format
      }
    }

    if (!isValidToken) {
      localStorage.removeItem('token'); // Clear invalid/expired token
      navigate('/login', { state: { from: location.pathname, autoOpenBooking: true } });
      return;
    }
    
    // Try to fetch user profile to prefill details
    try {
      const response = await api.get('/users/me');
      const user = response.data;
      
      let firstName = '';
      let lastName = '';
      if (user.name) {
        const parts = user.name.trim().split(' ');
        firstName = parts[0];
        lastName = parts.slice(1).join(' ');
      }
      
      setBookingForm(prev => ({
        ...prev,
        firstName: firstName || prev.firstName,
        lastName: lastName || prev.lastName,
        email: user.email || prev.email,
        phone: user.phone || prev.phone,
      }));
    } catch (e) {
      console.error('Error fetching profile, falling back to token:', e);
      // Fallback to token if endpoint fails
      try {
        if (token) {
          const payload = JSON.parse(atob(token.split('.')[1]));
          let firstName = '';
          let lastName = '';
          if (payload.name) {
            const parts = payload.name.trim().split(' ');
            firstName = parts[0];
            lastName = parts.slice(1).join(' ');
          }
          const sub = payload.sub || '';
          const isEmail = sub.includes('@');
          setBookingForm(prev => ({ 
            ...prev, 
            email: isEmail ? sub : (payload.email || prev.email),
            phone: !isEmail ? sub : prev.phone,
            firstName: firstName || prev.firstName,
            lastName: lastName || prev.lastName
          }));
        }
      } catch (err) {}
    }

    setShowBookingModal(true);
  };

  const handleBookingSubmit = async (e) => {
    e.preventDefault();
    
    // Basic validation
    if (!bookingForm.firstName || !bookingForm.email || !bookingForm.phone || !bookingForm.location) {
      toast.error('Please fill in all required fields');
      return;
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(bookingForm.email)) {
      toast.error('Please enter a valid email address');
      return;
    }

    // Phone validation (simple)
    if (bookingForm.phone.length < 10) {
      toast.error('Please enter a valid phone number');
      return;
    }

    setBookingLoading(true);
    try {
      await api.post('/bookings', {
        ...bookingForm,
        packageId: id,
        packageTitle: pkg.title,
        guests: guests,
        roomsBreakdown: allocatedRooms,
        selectedVehicle: selectedVehicle?.vehicleName,
        hotelCost: quote?.hotelCost,
        transportCost: quote?.transportCost,
        sightseeingCost: quote?.sightseeingCost,
        totalCost: quote?.totalCost,
        perAdultCost: perAdultCost,
        adultsCount: A,
        cwbCount: CWB,
        cnbCount: CNB,
        infantCount: I
      });
      toast.success('Booking submitted successfully! Check your email for confirmation.');
      setShowBookingModal(false);
      setBookingForm({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        location: '',
        bestTimeToReach: ''
      });
    } catch (error) {
      console.error('Booking error:', error);
      toast.error(error.response?.data?.error || 'Failed to submit booking. Please try again.');
    } finally {
      setBookingLoading(false);
    }
  };

  const handleSendSubmit = async (e) => {
    e.preventDefault();
    if (!sendForm.firstName || !sendForm.email || !sendForm.phone || !sendForm.location) {
      toast.error('Please fill in all required fields');
      return;
    }
    setSendLoading(true);
    try {
      await api.post('/bookings', {
        ...sendForm,
        packageId: id,
        packageTitle: pkg.title,
        isCustom: pkg.packageType === 'Custom',
        guests: guests,
        roomsBreakdown: allocatedRooms,
        selectedVehicle: selectedVehicle?.vehicleName,
        hotelCost: quote?.hotelCost,
        transportCost: quote?.transportCost,
        sightseeingCost: quote?.sightseeingCost,
        totalCost: quote?.totalCost,
        perAdultCost: perAdultCost,
        adultsCount: A,
        cwbCount: CWB,
        cnbCount: CNB,
        infantCount: I
      });
      toast.success('Itinerary PDF sent successfully!');
      setShowSendModal(false);
      setSendForm({ firstName: '', lastName: '', email: '', phone: '', location: '', bestTimeToReach: '' });
    } catch (err) {
      toast.error(err.response?.data?.error || 'Failed to send package');
    } finally {
      setSendLoading(false);
    }
  };

  // Combine thumbnail and gallery urls for the carousel
  const images = [];
  if (pkg?.media?.thumbnailUrl && pkg.media.thumbnailUrl.trim() !== '') {
    images.push(pkg.media.thumbnailUrl);
  }
  if (pkg?.media?.galleryUrls && Array.isArray(pkg.media.galleryUrls)) {
    pkg.media.galleryUrls.forEach(url => {
      if (url && url.trim() !== '' && !images.includes(url)) {
        images.push(url);
      }
    });
  }

  // Fallback image if empty
  if (images.length === 0) {
    images.push('https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?ixlib=rb-4.0.3&auto=format&fit=crop&w=1600&q=80');
  }

  useEffect(() => {
    if (images.length <= 1) return;
    
    const interval = setInterval(() => {
      setCurrentImageIndex((prev) => (prev + 1) % images.length);
    }, 5000);

    return () => clearInterval(interval);
  }, [images.length]);

  // Auto-open booking modal if redirecting from login
  useEffect(() => {
    if (pkg && location.state?.autoOpenBooking && !showBookingModal) {
      const state = { ...location.state };
      delete state.autoOpenBooking;
      navigate(location.pathname, { replace: true, state });
      
      handleBookNow();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pkg, location.state?.autoOpenBooking]);

  if (loading) {
    return <PackageDetailsSkeleton />;
  }

  if (!pkg) {
    return <div className="min-h-screen flex items-center justify-center flex-col">
      <h2 className="text-2xl font-bold mb-4">Package Not Found</h2>
      <Link to="/" className="text-blue-600 hover:underline flex items-center">
        <ArrowLeft className="w-4 h-4 mr-1" /> Back to Home
      </Link>
    </div>;
  }

  const getCategoryIcon = (catName) => {
    const matched = categories.find(c => c.name === catName);
    return matched ? matched.icon : 'HelpCircle';
  };

  const nextImage = () => setCurrentImageIndex((prev) => (prev + 1) % images.length);
  const prevImage = () => setCurrentImageIndex((prev) => (prev - 1 + images.length) % images.length);

  return (
    <div className="min-h-screen bg-gray-50 pb-12">
      <SEO 
        title={`${pkg.title} - ${pkg.destination}`} 
        description={`Book the ${pkg.title} package to ${pkg.destination}. ${pkg.duration?.days} days of unforgettable experience starting from ${pkg.pricing?.currency} ${formatCurrency(pkg.pricing?.finalPrice)}.`}
      />
      <main className="max-w-7xl mx-auto px-0 sm:px-6 lg:px-8 mt-0 sm:mt-8 w-full">
        <div className="bg-white shadow-none sm:shadow-lg sm:rounded-2xl overflow-hidden w-full max-w-full">
          {/* Image Carousel */}
          <div className="relative aspect-w-16 aspect-h-12 sm:aspect-h-7 lg:aspect-h-6 bg-gray-200">
            <img
              src={images[currentImageIndex]}
              alt={`Slide ${currentImageIndex + 1}`}
              className="object-cover w-full h-[40vh] sm:h-[60vh] transition-opacity duration-500"
            />
            
            {/* Carousel Controls */}
            {images.length > 1 && (
              <>
                <button
                  onClick={prevImage}
                  className="absolute left-2 sm:left-4 top-1/2 -translate-y-1/2 bg-black/30 hover:bg-black/50 text-white p-2 rounded-full backdrop-blur-sm transition-all"
                >
                  <ChevronLeft className="h-6 w-6" />
                </button>
                <button
                  onClick={nextImage}
                  className="absolute right-2 sm:right-4 top-1/2 -translate-y-1/2 bg-black/30 hover:bg-black/50 text-white p-2 rounded-full backdrop-blur-sm transition-all"
                >
                  <ChevronRight className="h-6 w-6" />
                </button>
                {/* Dots indicator */}
                <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex space-x-2">
                  {images.map((_, idx) => (
                    <button
                      key={idx}
                      onClick={() => setCurrentImageIndex(idx)}
                      className={`w-2 h-2 rounded-full transition-all ${currentImageIndex === idx ? 'bg-white w-4' : 'bg-white/50'}`}
                    />
                  ))}
                </div>
              </>
            )}
          </div>

          {/* Content */}
          <div className="p-4 sm:p-8 w-full overflow-hidden">
            <div className="flex flex-col lg:flex-row lg:justify-between lg:items-start mb-8 gap-6 w-full">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-3 text-sm text-blue-600 font-medium mb-3">
                  <span className="flex items-center">
                    <MapPin className="h-4 w-4 mr-1" />
                    {pkg.destination}
                  </span>
                  {pkg.packageCode && (
                    <span className="bg-blue-50 text-blue-700 px-2.5 py-0.5 rounded-md text-xs border border-blue-100">
                      {pkg.packageCode}
                    </span>
                  )}
                </div>
                <h1 className="text-3xl sm:text-4xl font-extrabold text-gray-900 leading-tight mb-4 break-words">
                  {pkg.title}
                </h1>
                <div 
                  className="text-gray-600 text-base sm:text-lg leading-relaxed quill-content overflow-hidden"
                  dangerouslySetInnerHTML={{ __html: cleanHtmlForDisplay(pkg.overview) }}
                />

                {/* Guest List & Custom Price Calculator */}
                <div className="mt-8 bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
                  <h3 className="text-lg font-bold text-gray-900 mb-2 flex items-center gap-2 font-display">
                    <Users className="h-5 w-5 text-blue-600 animate-pulse" />
                    Configure Travelers & Get Live Custom Quote
                  </h3>
                  <p className="text-xs text-gray-400 font-semibold mb-6">
                    Add guest details below to automatically calculate room configurations, transport recommendations, and exact package cost.
                  </p>

                  {/* Guest rows */}
                  <div className="space-y-3.5 mb-6">
                    {guests.map((g, idx) => (
                      <div key={idx} className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 bg-gray-50/50 p-3 rounded-xl border border-gray-100 hover:border-blue-200 transition-all animate-in fade-in duration-150">
                        <div className="flex-1 min-w-0">
                          <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">Traveler Name</label>
                          <input 
                            type="text" 
                            placeholder={`Traveler ${idx + 1}`}
                            className="w-full px-3 py-1.5 bg-white border border-gray-200 rounded-lg text-xs font-semibold focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all"
                            value={g.name}
                            onChange={(e) => {
                              const updated = [...guests];
                              updated[idx].name = e.target.value;
                              setGuests(updated);
                            }}
                          />
                        </div>
                        <div className="w-full sm:w-20">
                          <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">Age</label>
                          <input 
                            type="number" 
                            min="0"
                            placeholder="Age"
                            className="w-full px-3 py-1.5 bg-white border border-gray-200 rounded-lg text-xs font-bold text-center focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-all"
                            value={g.age}
                            onChange={(e) => {
                              const updated = [...guests];
                              const ageVal = parseInt(e.target.value);
                              updated[idx].age = isNaN(ageVal) ? '' : ageVal;
                              if (ageVal < 5 || ageVal >= 12) {
                                updated[idx].bedRequired = false;
                              }
                              setGuests(updated);
                            }}
                          />
                        </div>
                        
                        {/* Bed preference - only for child 5-11 */}
                        <div className="w-full sm:w-36 flex items-center gap-2">
                          <div className="flex-1">
                            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">Extra Bed Required</label>
                            {parseInt(g.age) >= 5 && parseInt(g.age) < 12 ? (
                              <label className="relative inline-flex items-center cursor-pointer mt-1">
                                <input 
                                  type="checkbox" 
                                  className="sr-only peer"
                                  checked={g.bedRequired}
                                  onChange={(e) => {
                                    const updated = [...guests];
                                    updated[idx].bedRequired = e.target.checked;
                                    setGuests(updated);
                                  }}
                                />
                                <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600"></div>
                                <span className="ml-2 text-xs font-bold text-gray-700">{g.bedRequired ? 'Yes' : 'No'}</span>
                              </label>
                            ) : (
                              <span className="text-[10px] text-gray-400 font-semibold block mt-1.5 italic">
                                {parseInt(g.age) >= 12 ? 'Adult (Included)' : parseInt(g.age) < 5 ? 'Infant (Free Room)' : 'Specify age...'}
                              </span>
                            )}
                          </div>
                        </div>

                        {/* Remove button */}
                        <div className="self-end sm:self-center mt-3 sm:mt-0">
                          {guests.length > 1 && (
                            <button 
                              type="button"
                              onClick={() => {
                                const updated = guests.filter((_, i) => i !== idx);
                                setGuests(updated);
                              }}
                              className="text-red-500 hover:text-red-700 hover:bg-red-50 p-2 rounded-lg transition-all"
                            >
                              <Trash2 size={16} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="flex flex-wrap gap-3 mb-6">
                    <button 
                      type="button"
                      onClick={() => setGuests([...guests, { name: '', age: 30, bedRequired: false }])}
                      className="bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold py-2 px-4 rounded-xl text-xs border border-blue-100 flex items-center gap-1.5 transition-all shadow-sm active:scale-95"
                    >
                      <Plus size={14} /> Add Guest
                    </button>
                    {guests.length > 0 && (
                      <button 
                        type="button"
                        onClick={() => setGuests([{ name: 'Traveler 1', age: 30, bedRequired: false }, { name: 'Traveler 2', age: 28, bedRequired: false }])}
                        className="bg-gray-50 hover:bg-gray-150 text-gray-600 font-bold py-2 px-4 rounded-xl text-xs border border-gray-200 flex items-center gap-1.5 transition-all shadow-sm active:scale-95"
                      >
                        Reset Guests
                      </button>
                    )}
                  </div>

                  {/* Warning if no adults */}
                  {A === 0 && guests.length > 0 && (
                    <div className="mb-6 bg-amber-50 border border-amber-200 p-4 rounded-xl text-xs font-semibold text-amber-700 flex items-start gap-2">
                      <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                      <span>At least one adult (Age 12 or above) is required in the guest list to calculate quotes and allocate rooms.</span>
                    </div>
                  )}

                  {/* Allocations Breakdown */}
                  {quote && A > 0 && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-5 border-t border-gray-100 animate-in fade-in duration-200">
                      {/* Room Allocation */}
                      <div className="space-y-4">
                        <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
                          <Hotel className="h-4 w-4 text-blue-600" />
                          Hotel Room Allocation
                        </h4>
                        
                        {/* List allocated rooms */}
                        <div className="space-y-2">
                          {allocatedRooms.map((r, i) => (
                            <div key={i} className="flex justify-between items-center p-3 bg-blue-50/20 border border-blue-50 rounded-xl shadow-sm">
                              <div>
                                <span className="text-[10px] font-black text-blue-700 bg-blue-50 px-2 py-0.5 rounded mr-2 uppercase">
                                  Room {i + 1}
                                </span>
                                <span className="text-xs font-bold text-gray-800">{r.tag}</span>
                              </div>
                              <span className="text-[10px] font-semibold text-gray-400">
                                {r.adults > 0 && `${r.adults} Adults`}
                                {r.cwb > 0 && `, ${r.cwb} CWB`}
                                {r.cnb > 0 && `, ${r.cnb} CNB`}
                                {r.infants > 0 && `, ${r.infants} Infant`}
                              </span>
                            </div>
                          ))}
                        </div>

                        {/* Room splits override options */}
                        {allocatedRooms.some(r => r.tag === "Triple Sharing") && (
                          <div className="bg-gray-50 p-3 rounded-xl border border-gray-150 flex items-center justify-between shadow-sm">
                            <div className="pr-3">
                              <span className="text-xs font-bold text-gray-800 block">Split Triple Sharing Room?</span>
                              <span className="text-[10px] text-gray-400 font-semibold block mt-0.5">Split Triple Room into 1 Double Sharing + 1 Single Occupancy room.</span>
                            </div>
                            <label className="relative inline-flex items-center cursor-pointer shrink-0">
                              <input 
                                type="checkbox" 
                                className="sr-only peer"
                                checked={overrideTriple}
                                onChange={(e) => setOverrideTriple(e.target.checked)}
                              />
                              <div className="w-9 h-5 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600"></div>
                            </label>
                          </div>
                        )}
                      </div>

                      {/* Transport Allocation */}
                      <div className="space-y-4">
                        <h4 className="text-xs font-bold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
                          <Car className="h-4 w-4 text-blue-600" />
                          Transport Allocation ({totalPax} Pax)
                        </h4>

                        {/* Recommended vehicle notice */}
                        {recommendedVehicle && (
                          <div className="bg-gray-50 p-3 rounded-xl border border-gray-150 text-xs shadow-sm">
                            <span className="font-bold text-gray-700 block">Recommended Vehicle:</span>
                            <span className="text-xs font-semibold text-gray-500 mt-1 block">
                              {recommendedVehicle.vehicleName} (Accommodates up to {recommendedVehicle.maxPax} Pax)
                            </span>
                          </div>
                        )}

                        {/* Vehicle override selection */}
                        {vehicles.length > 0 && (
                          <div>
                            <label className="block text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-2">Upgrade/Override Vehicle</label>
                            <select 
                              className="w-full px-3 py-2 bg-gray-50 border border-gray-200 rounded-xl text-xs font-bold focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                              value={vehicleOverride || recommendedVehicle?.vehicleName || ''}
                              onChange={(e) => setVehicleOverride(e.target.value)}
                            >
                              {vehicles.map((v, i) => (
                                <option key={i} value={v.vehicleName}>
                                  {v.vehicleName} (Max {v.maxPax} Pax) - ₹{formatCurrency(v.dailyRate)}/day
                                </option>
                              ))}
                            </select>
                          </div>
                        )}
                      </div>

                      {/* Pricing Quote Summary */}
                      <div className="md:col-span-2 bg-gray-50 p-4 border border-gray-100 rounded-2xl mt-2 space-y-3 shadow-inner">
                        <span className="text-xs font-bold text-gray-500 uppercase tracking-wider block">Custom Pricing Cost Sheet</span>
                        
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
                          <div className="bg-white p-3 border border-gray-150 rounded-xl shadow-sm">
                            <span className="text-[10px] text-gray-400 font-bold block uppercase tracking-wider mb-1">Hotel Cost ({nights} nights)</span>
                            <span className="text-base font-extrabold text-gray-800">₹{formatCurrency(quote.hotelCost)}</span>
                            <span className="block text-[9px] text-gray-400 mt-0.5">{allocatedRooms.length} rooms mapped</span>
                          </div>
                          
                          <div className="bg-white p-3 border border-gray-150 rounded-xl shadow-sm">
                            <span className="text-[10px] text-gray-400 font-bold block uppercase tracking-wider mb-1">Transport Cost ({days} days)</span>
                            <span className="text-base font-extrabold text-gray-800">₹{formatCurrency(quote.transportCost)}</span>
                            <span className="block text-[9px] text-gray-400 mt-0.5">{selectedVehicle?.vehicleName || 'None'} selected</span>
                          </div>
                          
                          <div className="bg-white p-3 border border-gray-150 rounded-xl shadow-sm">
                            <span className="text-[10px] text-gray-400 font-bold block uppercase tracking-wider mb-1">Sightseeing Tickets</span>
                            <span className="text-base font-extrabold text-gray-800">₹{formatCurrency(quote.sightseeingCost)}</span>
                            <span className="block text-[9px] text-gray-400 mt-0.5">{totalPax} headcounts mapped</span>
                          </div>
                        </div>

                        <div className="border-t border-gray-200 pt-3 flex justify-between items-center flex-wrap gap-2">
                          <div>
                            <span className="text-[10px] text-gray-400 font-black uppercase tracking-wider block">Grand Dynamic Quote Total</span>
                            <span className="text-2xl font-black text-blue-600">₹{formatCurrency(quote.totalCost)}</span>
                          </div>
                          <div className="text-right">
                            <span className="text-[10px] text-gray-400 font-black uppercase tracking-wider block">Per Adult Share</span>
                            <span className="text-xl font-black text-gray-800">₹{formatCurrency(perAdultCost)} <span className="text-[10px] font-semibold text-gray-500">/ adult</span></span>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Pricing Card */}
              <div className="bg-blue-50/50 border border-blue-100 rounded-xl p-6 lg:w-80 flex-shrink-0">
                <div className="flex items-center text-gray-600 mb-4">
                  <Clock className="h-5 w-5 mr-2 text-blue-500" />
                  <span className="font-medium text-lg">{pkg.duration?.days} Days / {pkg.duration?.nights} Nights</span>
                </div>
                <div className="mb-2">
                  <span className="text-gray-500 text-sm font-medium uppercase tracking-wide">
                    {quote ? 'Estimated Quote' : 'Starting from'}
                  </span>
                </div>
                <div className="flex flex-col mb-1">
                  <span className="text-4xl font-extrabold text-gray-900 tracking-tight">
                    {pkg.pricing?.currency || 'INR'} {formatCurrency(quote ? quote.totalCost : pkg.pricing?.finalPrice)}
                  </span>
                  {quote && A > 0 && (
                    <span className="text-sm text-indigo-600 font-bold mt-1 bg-indigo-50 border border-indigo-100 rounded-lg px-2.5 py-1 inline-block self-start">
                      ₹{formatCurrency(perAdultCost)} per adult ({A} Adults)
                    </span>
                  )}
                  {!quote && pkg.pricing?.basePrice > pkg.pricing?.finalPrice && (
                    <span className="mt-1 text-sm text-gray-400 line-through decoration-red-400">
                      {pkg.pricing?.currency || 'INR'} {formatCurrency(pkg.pricing?.basePrice)}
                    </span>
                  )}
                </div>
                <div className="text-xs text-gray-500 italic mb-6">
                  {numberToWords(quote ? quote.totalCost : pkg.pricing?.finalPrice)}
                </div>
                <button 
                  onClick={handleBookNow}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3.5 px-4 rounded-lg shadow-sm hover:shadow-md transition-all"
                >
                  Book Now
                </button>

                {isAdmin && (
                  <button 
                    onClick={() => setShowSendModal(true)}
                    className="w-full mt-3 bg-purple-600 hover:bg-purple-700 text-white font-semibold py-3 px-4 rounded-lg shadow-sm hover:shadow-md transition-all flex items-center justify-center gap-2"
                  >
                    <Mail size={18} />
                    Send to Customer
                  </button>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 mb-12">
              {/* Inclusions */}
              {pkg.inclusions && pkg.inclusions.length > 0 && (
                <div className="bg-gray-50 rounded-xl p-6 border border-gray-100">
                  <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                    <CheckCircle2 className="h-6 w-6 text-green-500 mr-2" />
                    What's Included
                  </h3>
                  <ul className="space-y-3 list-none">
                    {pkg.inclusions.map((item, idx) => (
                      <li key={idx} className="flex items-start text-sm sm:text-base">
                        <CheckCircle2 className="h-5 w-5 text-green-500 mr-3 flex-shrink-0 mt-0.5" />
                        <span className="text-gray-700 break-words">{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Exclusions */}
              {pkg.exclusions && pkg.exclusions.length > 0 && (
                <div className="bg-gray-50 rounded-xl p-6 border border-gray-100">
                  <h3 className="text-xl font-bold text-gray-900 mb-4 flex items-center">
                    <XCircle className="h-6 w-6 text-red-500 mr-2" />
                    What's Excluded
                  </h3>
                  <ul className="space-y-3 list-none">
                    {pkg.exclusions.map((item, idx) => (
                      <li key={idx} className="flex items-start text-sm sm:text-base">
                        <XCircle className="h-5 w-5 text-red-500 mr-3 flex-shrink-0 mt-0.5" />
                        <span className="text-gray-700 break-words">{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Attached Configurations / Customizations */}
            {attachedConfigs.length > 0 && (
              <div className="border-t border-gray-200 pt-10 mb-10">
                <h2 className="text-2xl font-bold text-gray-900 mb-6 flex items-center gap-2.5">
                  <Settings className="h-6 w-6 text-blue-600" />
                  Optional Activities & Configuration Customizations
                </h2>
                <div className="space-y-6">
                  {attachedConfigs.map((config) => (
                    <div key={config.id} className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
                      <div className="flex flex-col sm:flex-row justify-between sm:items-center border-b pb-3 mb-4 gap-2">
                        <div>
                          <h3 className="font-extrabold text-gray-900 text-lg leading-tight">
                            {config.title}
                          </h3>
                          {config.description && (
                            <p className="text-sm text-gray-500 mt-1">
                              {config.description}
                            </p>
                          )}
                        </div>
                        <span className="bg-indigo-50 text-indigo-700 px-3 py-1 rounded-full text-xs font-bold shrink-0 self-start sm:self-center">
                          {config.configCode}
                        </span>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {(config.options || []).map((opt, idx) => {
                          const iconName = getCategoryIcon(opt.categoryName);
                          const IconComponent = ICON_MAP[iconName] || HelpCircle;
                          return (
                            <div key={idx} className="flex items-center justify-between p-4 bg-gray-50/50 rounded-xl border border-gray-100 hover:border-blue-450 transition-all shadow-sm">
                              <div className="flex items-center gap-3.5 min-w-0 flex-1">
                                <div className="p-2.5 bg-blue-50 text-blue-600 rounded-lg shadow-sm">
                                  <IconComponent className="h-5 w-5" />
                                </div>
                                <div className="min-w-0 flex-1">
                                  <span className="text-[10px] font-extrabold uppercase tracking-wider text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                                    {opt.categoryName}
                                  </span>
                                  <p className="text-xs font-bold text-gray-700 mt-1 leading-snug">
                                    {opt.optionName}
                                  </p>
                                </div>
                              </div>
                              <div className="text-right pl-3 shrink-0">
                                <span className="text-sm font-black text-gray-900 block">
                                  ₹{formatCurrency(opt.price)}
                                </span>
                                <span className="text-[9px] font-bold text-gray-400 uppercase tracking-wider">
                                  Add-on Price
                                </span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Itinerary */}
            {pkg.itinerary && pkg.itinerary.length > 0 && (
              <div className="border-t border-gray-200 pt-10">
                <h2 className="text-2xl font-bold text-gray-900 mb-8 flex items-center">
                  <Calendar className="h-7 w-7 text-blue-600 mr-3" />
                  Detailed Itinerary
                </h2>
                <div className="space-y-8 relative before:absolute before:inset-0 before:ml-5 before:-translate-x-px md:before:mx-auto md:before:translate-x-0 before:h-full before:w-0.5 before:bg-gradient-to-b before:from-transparent before:via-gray-200 before:to-transparent">
                  {pkg.itinerary.map((day, idx) => (
                    <div key={idx} className="relative flex flex-col md:flex-row items-start w-full mb-8">
                      {/* Timeline dot */}
                      <div className="absolute left-0 md:left-1/2 -ml-3 md:-ml-3 mt-5 h-6 w-6 rounded-full border-4 border-white bg-blue-500 shadow-sm z-10 flex items-center justify-center"></div>
                      
                      {/* Left or Right spacing block for alternating timeline */}
                      {idx % 2 !== 0 && <div className="hidden md:block md:w-1/2"></div>}
                      
                      {/* Content Card */}
                      <div className={`ml-10 md:ml-0 md:w-1/2 flex flex-col w-[calc(100%-2.5rem)] ${idx % 2 === 0 ? 'md:pr-10' : 'md:pl-10'}`}>
                        <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow w-full">
                          <div className="flex flex-col sm:flex-row items-center justify-center gap-2 mb-4 text-center">
                            <span className="bg-blue-100 text-blue-800 text-xs font-bold px-2.5 py-1 rounded shrink-0">Day {day.day}</span>
                            <h4 className="text-lg font-bold text-gray-900 leading-tight">{day.title}</h4>
                          </div>
                          <div 
                            className="text-gray-600 text-sm md:text-base leading-relaxed quill-content overflow-hidden"
                            dangerouslySetInnerHTML={{ __html: cleanHtmlForDisplay(day.activities) }}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Special Notes / Conditions */}
            {!isHtmlEmpty(pkg.specialNotes) && (
              <div id="special-notes" className="mt-12 bg-red-50/50 border-2 border-red-100 rounded-2xl p-8 shadow-sm">
                <h3 className="text-2xl font-bold text-red-600 mb-6 flex items-center">
                  <span className="bg-red-600 text-white p-1 rounded mr-3 flex items-center justify-center">
                    <CheckCircle2 size={18} />
                  </span>
                  Important Notes & Conditions
                </h3>
                <div 
                  className="text-gray-800 text-lg leading-relaxed quill-content prose max-w-none overflow-hidden"
                  dangerouslySetInnerHTML={{ __html: cleanHtmlForDisplay(pkg.specialNotes) }}
                />
              </div>
            )}

            {/* Global Terms & Conditions */}
            {!isHtmlEmpty(globalTerms) && (
              <div className="mt-16 border-t border-gray-100 pt-10 overflow-hidden">
                <h4 className="text-sm font-bold text-gray-500 uppercase tracking-wider mb-4 px-2">Terms & Conditions</h4>
                <div 
                  className="text-gray-500 text-xs sm:text-sm leading-relaxed quill-content prose prose-sm max-w-none px-2 opacity-80"
                  dangerouslySetInnerHTML={{ __html: cleanHtmlForDisplay(globalTerms) }}
                />
              </div>
            )}
          </div>
        </div>
      </main>

      {/* Booking Modal */}
      {showBookingModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in duration-200">
            <div className="flex items-center justify-between p-6 border-b">
              <h3 className="text-xl font-bold text-gray-900">Book Your Trip</h3>
              <button 
                onClick={() => setShowBookingModal(false)}
                className="text-gray-400 hover:text-gray-600 transition-colors"
              >
                <X size={24} />
              </button>
            </div>
            
            <form onSubmit={handleBookingSubmit} className="p-6 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">First Name *</label>
                  <input
                    type="text"
                    required
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    value={bookingForm.firstName}
                    onChange={(e) => setBookingForm({...bookingForm, firstName: e.target.value})}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
                  <input
                    type="text"
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                    value={bookingForm.lastName}
                    onChange={(e) => setBookingForm({...bookingForm, lastName: e.target.value})}
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email Address *</label>
                <input
                  type="email"
                  required
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  value={bookingForm.email}
                  onChange={(e) => setBookingForm({...bookingForm, email: e.target.value})}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Phone Number *</label>
                <input
                  type="tel"
                  required
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  value={bookingForm.phone}
                  onChange={(e) => setBookingForm({...bookingForm, phone: e.target.value})}
                  placeholder="+91 98765 43210"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Your Location/City *</label>
                <input
                  type="text"
                  required
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  value={bookingForm.location}
                  onChange={(e) => setBookingForm({...bookingForm, location: e.target.value})}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Best time to reach (Optional)</label>
                <input
                  type="text"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  value={bookingForm.bestTimeToReach}
                  onChange={(e) => setBookingForm({...bookingForm, bestTimeToReach: e.target.value})}
                  placeholder="e.g. 5 PM - 7 PM"
                />
              </div>

              <div className="pt-4">
                <button
                  type="submit"
                  disabled={bookingLoading}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-4 rounded-xl shadow-lg hover:shadow-xl transition-all disabled:opacity-50 flex items-center justify-center gap-2"
                >
                  {bookingLoading ? (
                    <>
                      <Loader2 className="animate-spin" size={20} />
                      Processing...
                    </>
                  ) : (
                    'Confirm Booking'
                  )}
                </button>
                <p className="text-center text-xs text-gray-500 mt-3">
                  By clicking confirm, you agree to our Terms & Conditions.
                </p>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* Send to Customer Modal (Admin Only) */}
      {isAdmin && showSendModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in duration-200">
            <div className="flex items-center justify-between p-6 border-b bg-purple-600 text-white">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-white/20 rounded-lg">
                  <Mail size={24} />
                </div>
                <div>
                  <h3 className="text-lg font-bold">Send Itinerary to Customer</h3>
                  <p className="text-xs text-purple-100 opacity-90 truncate max-w-[300px]">
                    {pkg?.packageCode}: {pkg?.title}
                  </p>
                </div>
              </div>
              <button onClick={() => setShowSendModal(false)} className="text-white/80 hover:text-white transition-colors">
                <X size={24} />
              </button>
            </div>
            
            <form onSubmit={handleSendSubmit} className="p-6 space-y-5">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">First Name *</label>
                  <div className="relative">
                    <User size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <input
                      type="text"
                      required
                      className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                      value={sendForm.firstName}
                      onChange={(e) => setSendForm({...sendForm, firstName: e.target.value})}
                      placeholder="John"
                    />
                  </div>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">Last Name</label>
                  <input
                    type="text"
                    className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                    value={sendForm.lastName}
                    onChange={(e) => setSendForm({...sendForm, lastName: e.target.value})}
                    placeholder="Doe"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">Email Address *</label>
                <div className="relative">
                  <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="email"
                    required
                    className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                    value={sendForm.email}
                    onChange={(e) => setSendForm({...sendForm, email: e.target.value})}
                    placeholder="customer@example.com"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">Phone Number *</label>
                <div className="relative">
                  <Phone size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="tel"
                    required
                    className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                    value={sendForm.phone}
                    onChange={(e) => setSendForm({...sendForm, phone: e.target.value})}
                    placeholder="+91 98765 43210"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">Location/City *</label>
                <div className="relative">
                  <MapPinIcon size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    required
                    className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                    value={sendForm.location}
                    onChange={(e) => setSendForm({...sendForm, location: e.target.value})}
                    placeholder="Mumbai, India"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-bold text-gray-500 uppercase tracking-wider ml-1">Best time to reach (Optional)</label>
                <div className="relative">
                  <Clock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                  <input
                    type="text"
                    className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-xl focus:ring-2 focus:ring-purple-500 focus:border-purple-500 transition-all"
                    value={sendForm.bestTimeToReach}
                    onChange={(e) => setSendForm({...sendForm, bestTimeToReach: e.target.value})}
                    placeholder="e.g. 5 PM - 7 PM"
                  />
                </div>
              </div>

              <div className="pt-4 flex gap-3">
                <button
                  type="button"
                  onClick={() => setShowSendModal(false)}
                  className="flex-1 px-6 py-3 border border-gray-200 text-gray-600 font-bold rounded-xl hover:bg-gray-50 transition-all"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={sendLoading}
                  className="flex-[2] bg-purple-600 hover:bg-purple-700 text-white font-bold py-3 px-6 rounded-xl shadow-lg hover:shadow-xl transition-all disabled:opacity-50 flex items-center justify-center gap-2"
                >
                  {sendLoading ? (
                    <>
                      <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      Sending...
                    </>
                  ) : (
                    <>
                      <Send size={18} />
                      Send Itinerary
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default PackageDetails;
