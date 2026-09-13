import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import MainLayout from '../components/MainLayout';
import { createTrip, addLeg, bookLeg, createPaymentOrder, getPaymentStatus } from '../services/api';

const LEG_TYPES = ['RAIL', 'FLIGHT', 'HOTEL', 'CAB'];

const decodeJwtPayload = (token) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    return null;
  }
};

const loadRazorpayScript = () =>
  new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });

const STEP = {
  BUILD: 'BUILD',
  CONTACT: 'CONTACT',
  PROCESSING: 'PROCESSING',
  CONFIRMED: 'CONFIRMED',
  FAILED: 'FAILED',
};

const Checkout = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(STEP.BUILD);
  const [legType, setLegType] = useState(LEG_TYPES[0]);
  const [pricePaise, setPricePaise] = useState(150000);
  const [email, setEmail] = useState(() => {
    const token = localStorage.getItem('token');
    const payload = token ? decodeJwtPayload(token) : null;
    return payload && payload.sub && payload.sub.includes('@') ? payload.sub : '';
  });
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [tripId, setTripId] = useState(null);
  const [legId, setLegId] = useState(null);

  const handleBuildLeg = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const trip = await createTrip({
        title: `${legType} booking`,
        travellerIds: [],
        origin: 'N/A',
        destination: 'N/A',
      });
      const leg = await addLeg(trip.id, {
        type: legType,
        pricePaise: Number(pricePaise),
        startAt: new Date().toISOString(),
        endAt: new Date().toISOString(),
        metadata: {},
      });
      setTripId(trip.id);
      setLegId(leg.id);
      setStep(STEP.CONTACT);
    } catch (err) {
      setError('Could not create your trip/leg. Please try again.');
    }
  };

  const pollPaymentStatus = (bookingRef, attemptsLeft) => {
    if (attemptsLeft <= 0) {
      setStep(STEP.FAILED);
      setError('Still processing - please check back shortly.');
      return;
    }
    getPaymentStatus(bookingRef)
      .then((payment) => {
        if (payment.status === 'CAPTURED') {
          setStep(STEP.CONFIRMED);
        } else if (payment.status === 'FAILED') {
          setStep(STEP.FAILED);
          setError('Payment failed. Please try again.');
        } else {
          setTimeout(() => pollPaymentStatus(bookingRef, attemptsLeft - 1), 3000);
        }
      })
      .catch(() => {
        setTimeout(() => pollPaymentStatus(bookingRef, attemptsLeft - 1), 3000);
      });
  };

  const handleContactSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setStep(STEP.PROCESSING);
    try {
      const booked = await bookLeg(tripId, legId, { email, phone });
      const payment = await createPaymentOrder({
        bookingRef: booked.id,
        amountPaise: booked.pricePaise,
        method: 'razorpay',
        quoteToken: booked.quoteToken,
      });

      if (payment.status === 'REJECTED') {
        setStep(STEP.FAILED);
        setError('Your quote expired. Please start again.');
        return;
      }

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded) {
        setStep(STEP.FAILED);
        setError('Could not load the payment provider. Please try again.');
        return;
      }

      const razorpay = new window.Razorpay({
        key: import.meta.env.VITE_RAZORPAY_KEY_ID,
        order_id: payment.providerRef,
        amount: payment.amountPaise,
        currency: 'INR',
        name: 'Travel2Go',
        handler: () => {
          // Do NOT trust this callback - it only means the modal closed
          // successfully client-side. The real answer is the webhook,
          // surfaced via the status poll below.
        },
        modal: {
          ondismiss: () => {
            pollPaymentStatus(booked.id, 30);
          },
        },
      });
      razorpay.on('payment.failed', () => {
        pollPaymentStatus(booked.id, 30);
      });
      razorpay.open();
      pollPaymentStatus(booked.id, 30);
    } catch (err) {
      setStep(STEP.FAILED);
      if (err.response && err.response.status === 409) {
        setError('This leg is already claimed by another user.');
      } else {
        setError('Something went wrong while booking. Please try again.');
      }
    }
  };

  return (
    <MainLayout>
      <div className="max-w-lg mx-auto py-12 px-4">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">Book a leg</h1>

        {error && (
          <div className="mb-4 bg-red-50 border-l-4 border-red-400 p-4">
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {step === STEP.BUILD && (
          <form className="space-y-4" onSubmit={handleBuildLeg}>
            <div>
              <label className="block text-sm font-medium text-gray-700">Type</label>
              <select
                value={legType}
                onChange={(e) => setLegType(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              >
                {LEG_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Price (paise)</label>
              <input
                type="number"
                required
                min="1"
                value={pricePaise}
                onChange={(e) => setPricePaise(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Continue
            </button>
          </form>
        )}

        {step === STEP.CONTACT && (
          <form className="space-y-4" onSubmit={handleContactSubmit}>
            <div>
              <label className="block text-sm font-medium text-gray-700">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Phone</label>
              <input
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="mt-1 block w-full border border-gray-300 rounded-md px-3 py-2"
              />
            </div>
            <button
              type="submit"
              className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Pay now
            </button>
          </form>
        )}

        {step === STEP.PROCESSING && (
          <div className="text-center py-12">
            <p className="text-gray-600">Confirming your payment...</p>
          </div>
        )}

        {step === STEP.CONFIRMED && (
          <div className="text-center py-12">
            <p className="text-xl font-semibold text-green-700">Booking confirmed!</p>
            <button onClick={() => navigate('/')} className="mt-4 text-blue-600 underline">
              Back home
            </button>
          </div>
        )}

        {step === STEP.FAILED && (
          <div className="text-center py-12">
            <button
              onClick={() => setStep(STEP.CONTACT)}
              className="py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              Try again
            </button>
          </div>
        )}
      </div>
    </MainLayout>
  );
};

export default Checkout;
