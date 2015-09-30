package com.esipeng.diameter.node;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Capability
{
  Set<Integer> supported_vendor;
  Set<Integer> auth_app;
  Set<Integer> acct_app;
  Set<VendorApplication> auth_vendor;
  Set<VendorApplication> acct_vendor;
  
  static class VendorApplication
  {
    public int vendor_id;
    public int application_id;
    
    public VendorApplication(int vendor, int app)
    {
      this.vendor_id = vendor;
      this.application_id = app;
    }
    
    public int hashCode() { return this.vendor_id + this.application_id; }
    
    public boolean equals(Object other) {
      if (this == other)
        return true;
      if ((other == null) || (other.getClass() != getClass()))
        return false;
      return (((VendorApplication)other).vendor_id == this.vendor_id) && (((VendorApplication)other).application_id == this.application_id);
    }
  }



  public Capability()
  {
    this.supported_vendor = new HashSet<Integer>();
    this.auth_app = new HashSet<Integer>();
    this.acct_app = new HashSet<Integer>();
    this.auth_vendor = new HashSet<VendorApplication>();
    this.acct_vendor = new HashSet<VendorApplication>();
  }
  
  public Capability(Capability other)
  {
    this.supported_vendor = new HashSet<Integer>();
    for (Iterator<Integer> itSupportedVendor = other.supported_vendor.iterator(); itSupportedVendor.hasNext();) {
      Integer vendor = itSupportedVendor.next();
      this.supported_vendor.add(vendor);
    }

    this.auth_app = new HashSet<Integer>();
    for (Iterator<Integer> itAuthApp = other.auth_app.iterator(); itAuthApp.hasNext();) {
      Integer auth = itAuthApp.next();
      this.auth_app.add(auth);
    }

    this.acct_app = new HashSet<Integer>();
    for (Iterator<Integer> itAcct = other.acct_app.iterator(); itAcct.hasNext();) {
      Integer acct = itAcct.next();
      this.acct_app.add(acct);
    }

    this.auth_vendor = new HashSet<VendorApplication>();
    for (Iterator<VendorApplication> authVendor = other.auth_vendor.iterator(); authVendor.hasNext();) {
      VendorApplication vendorApplication = authVendor.next();
      this.auth_vendor.add(vendorApplication);
    }

    this.acct_vendor = new HashSet<VendorApplication>();
    for (Iterator<VendorApplication> itVendorApp = other.acct_vendor.iterator(); itVendorApp.hasNext();) {
        VendorApplication acctVendor = itVendorApp.next();
        this.acct_vendor.add(acctVendor);
    }
  }
  
  public boolean isSupportedVendor(int vendorId) {
    return this.supported_vendor.contains(Integer.valueOf(vendorId));
  }
  


  public boolean isAllowedAuthApp(int authId)
  {
    return (this.auth_app.contains(Integer.valueOf(authId))) || (this.auth_app.contains(Integer.valueOf(-1)));
  }
  



  public boolean isAllowedAcctApp(int authId)
  {
    return (this.acct_app.contains(Integer.valueOf(authId))) || (this.acct_app.contains(Integer.valueOf(-1)));
  }
  


  public boolean isAllowedAuthApp(int vendor, int app)
  {
    return this.auth_vendor.contains(new VendorApplication(vendor, app));
  }
  

  public boolean isAllowedAcctApp(int vendor, int app)
  {
    return this.acct_vendor.contains(new VendorApplication(vendor, app));
  }
  
  public void addSupportedVendor(int vendor) {
    this.supported_vendor.add(Integer.valueOf(vendor));
  }
  
  public void addAuthApp(int auth) { this.auth_app.add(Integer.valueOf(auth)); }
  
  public void addAcctApp(int acct) {
    this.acct_app.add(Integer.valueOf(acct));
  }
  
  public void addVendorAuthApp(int vendor, int auth) { this.auth_vendor.add(new VendorApplication(vendor, auth)); }
  
  public void addVendorAcctApp(int vendor, int auth) {
    this.acct_vendor.add(new VendorApplication(vendor, auth));
  }
  


  public boolean isEmpty()
  {
    return (this.auth_app.isEmpty()) && (this.acct_app.isEmpty()) && (this.auth_vendor.isEmpty()) && (this.acct_vendor.isEmpty());
  }
  






  static Capability calculateIntersection(Capability capa1, Capability capa2)
  {
    Capability intersectCapability = new Capability();
    for (Iterator<Integer> itSupportedVendor = capa2.supported_vendor.iterator(); itSupportedVendor.hasNext();) {
        Integer vendorId = itSupportedVendor.next();
      if (capa1.isSupportedVendor(vendorId))
        intersectCapability.addSupportedVendor(vendorId);
    }

    for (Iterator<Integer> authApp = capa2.auth_app.iterator(); authApp.hasNext();) {
        Integer auth = authApp.next();
      if ((auth == -1) || (capa1.auth_app.contains(auth)) || (capa1.auth_app.contains(Integer.valueOf(-1))))
      {
        intersectCapability.addAuthApp(auth);
      }
    }

    for (Iterator<Integer> itAcctApp = capa2.acct_app.iterator(); itAcctApp.hasNext();) {
        Integer acctId = itAcctApp.next();
      if ((acctId == -1) || (capa1.acct_app.contains(acctId)) || (capa1.acct_app.contains(Integer.valueOf(-1))))
      {

        intersectCapability.addAcctApp(acctId);
      }
    }

    for (Iterator<VendorApplication> itAuthVendor = capa2.auth_vendor.iterator(); itAuthVendor.hasNext();) {
        VendorApplication authVendor = itAuthVendor.next();
      
      if (capa1.isAllowedAuthApp((authVendor).vendor_id, (authVendor).application_id))
        intersectCapability.addVendorAuthApp( authVendor.vendor_id, authVendor.application_id);
    }

    for (Iterator<VendorApplication> itAcctVendor = capa2.acct_vendor.iterator(); itAcctVendor.hasNext();) {
        VendorApplication acctVendor = itAcctVendor.next();
      
      if (capa1.isAllowedAcctApp(acctVendor.vendor_id, acctVendor.application_id))
        intersectCapability.addVendorAcctApp( acctVendor.vendor_id,  acctVendor.application_id);
    }
    return intersectCapability;
  }
}
