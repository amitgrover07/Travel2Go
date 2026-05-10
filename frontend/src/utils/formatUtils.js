export const formatCurrency = (amount, currency = 'INR') => {
  if (amount === undefined || amount === null) return '';
  return new Intl.NumberFormat('en-IN', {
    style: 'decimal',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
};

export const numberToWords = (num) => {
  if (num === 0) return 'Zero';
  if (!num) return '';

  const a = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
  const b = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

  const convert = (n) => {
    if (n < 20) return a[n];
    if (n < 100) return b[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + a[n % 10] : '');
    if (n < 1000) return a[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' and ' + convert(n % 100) : '');
    return '';
  };

  let result = '';
  
  // Handling Crores, Lakhs, Thousands for Indian Numbering System
  if (num >= 10000000) {
    result += convert(Math.floor(num / 10000000)) + ' Crore ';
    num %= 10000000;
  }
  if (num >= 100000) {
    result += convert(Math.floor(num / 100000)) + ' Lakh ';
    num %= 100000;
  }
  if (num >= 1000) {
    result += convert(Math.floor(num / 1000)) + ' Thousand ';
    num %= 1000;
  }
  if (num > 0) {
    result += convert(num);
  }

  return result.trim() + ' Only';
};

export const isHtmlEmpty = (html) => {
  if (!html) return true;
  // Strip HTML tags and replace &nbsp; with space
  const stripped = html.replace(/<[^>]*>?/gm, '').replace(/&nbsp;/g, ' ').trim();
  // Check if anything is left (text) or if there are images/iframes
  return stripped.length === 0 && !html.includes('<img') && !html.includes('<iframe');
};

export const cleanHtmlForDisplay = (html) => {
  if (!html) return '';
  let clean = html;
  
  // 1. Merge words split by \n or <br> mid-word (lowercase/hyphen followed by break followed by lowercase)
  clean = clean.replace(/([a-z-])\s*(?:<br\s*\/?>|\n)\s*([a-z])/g, '$1$2');
  
  // 2. Replace any remaining \n with a space
  clean = clean.replace(/\n/g, ' ');
  
  // 3. Replace any remaining <br> with a space
  clean = clean.replace(/<br\s*\/?>/gi, ' ');
  
  return clean;
};
