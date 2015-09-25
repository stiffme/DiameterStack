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
    
    public VendorApplication(int paramInt1, int paramInt2)
    {
      this.vendor_id = paramInt1;
      this.application_id = paramInt2;
    }
    
    public int hashCode() { return this.vendor_id + this.application_id; }
    
    public boolean equals(Object paramObject) {
      if (this == paramObject)
        return true;
      if ((paramObject == null) || (paramObject.getClass() != getClass()))
        return false;
      return (((VendorApplication)paramObject).vendor_id == this.vendor_id) && (((VendorApplication)paramObject).application_id == this.application_id);
    }
  }
  









  public Capability()
  {
    this.supported_vendor = new HashSet();
    this.auth_app = new HashSet();
    this.acct_app = new HashSet();
    this.auth_vendor = new HashSet();
    this.acct_vendor = new HashSet();
  }
  
  public Capability(Capability paramCapability)
  {
    this.supported_vendor = new HashSet();
			  //Object localObject;
    for (Iterator localIterator = paramCapability.supported_vendor.iterator(); localIterator.hasNext();) { Integer localObject = (Integer)localIterator.next();
      this.supported_vendor.add(localObject); }
    Object localObject; this.auth_app = new HashSet();
    for (Iterator localIterator = paramCapability.auth_app.iterator(); localIterator.hasNext();) {Integer localObject2 = (Integer)localIterator.next();
      this.auth_app.add(localObject2); }
    this.acct_app = new HashSet();
    for (Iterator localIterator = paramCapability.acct_app.iterator(); localIterator.hasNext();) {Integer localObject3 = (Integer)localIterator.next();
      this.acct_app.add(localObject3); }
    this.auth_vendor = new HashSet();
    for (Iterator localIterator = paramCapability.auth_vendor.iterator(); localIterator.hasNext();) {VendorApplication localObject4 = (VendorApplication)localIterator.next();
      this.auth_vendor.add(localObject4); }
    this.acct_vendor = new HashSet();
    for (Iterator localIterator = paramCapability.acct_vendor.iterator(); localIterator.hasNext();) {VendorApplication localObject5 = (VendorApplication)localIterator.next();
      this.acct_vendor.add(localObject5);
    }
  }
  
  public boolean isSupportedVendor(int paramInt) {
    return this.supported_vendor.contains(Integer.valueOf(paramInt));
  }
  


  public boolean isAllowedAuthApp(int paramInt)
  {
    return (this.auth_app.contains(Integer.valueOf(paramInt))) || (this.auth_app.contains(Integer.valueOf(-1)));
  }
  



  public boolean isAllowedAcctApp(int paramInt)
  {
    return (this.acct_app.contains(Integer.valueOf(paramInt))) || (this.acct_app.contains(Integer.valueOf(-1)));
  }
  


  public boolean isAllowedAuthApp(int paramInt1, int paramInt2)
  {
    return this.auth_vendor.contains(new VendorApplication(paramInt1, paramInt2));
  }
  

  public boolean isAllowedAcctApp(int paramInt1, int paramInt2)
  {
    return this.acct_vendor.contains(new VendorApplication(paramInt1, paramInt2));
  }
  
  public void addSupportedVendor(int paramInt) {
    this.supported_vendor.add(Integer.valueOf(paramInt));
  }
  
  public void addAuthApp(int paramInt) { this.auth_app.add(Integer.valueOf(paramInt)); }
  
  public void addAcctApp(int paramInt) {
    this.acct_app.add(Integer.valueOf(paramInt));
  }
  
  public void addVendorAuthApp(int paramInt1, int paramInt2) { this.auth_vendor.add(new VendorApplication(paramInt1, paramInt2)); }
  
  public void addVendorAcctApp(int paramInt1, int paramInt2) {
    this.acct_vendor.add(new VendorApplication(paramInt1, paramInt2));
  }
  


  public boolean isEmpty()
  {
    return (this.auth_app.isEmpty()) && (this.acct_app.isEmpty()) && (this.auth_vendor.isEmpty()) && (this.acct_vendor.isEmpty());
  }
  






  static Capability calculateIntersection(Capability paramCapability1, Capability paramCapability2)
  {
    Capability localCapability = new Capability();
    for (Iterator localIterator = paramCapability2.supported_vendor.iterator(); localIterator.hasNext();) {Integer localObject = (Integer)localIterator.next();
      if (paramCapability1.isSupportedVendor(((Integer)localObject).intValue()))
        localCapability.addSupportedVendor(((Integer)localObject).intValue());
    }
    Object localObject;
    for (Iterator localIterator = paramCapability2.auth_app.iterator(); localIterator.hasNext();) {Integer localObject2 = (Integer)localIterator.next();
      if ((((Integer)localObject2).intValue() == -1) || (paramCapability1.auth_app.contains(localObject2)) || (paramCapability1.auth_app.contains(Integer.valueOf(-1))))
      {

        localCapability.addAuthApp(((Integer)localObject2).intValue()); }
    }
    for (Iterator localIterator = paramCapability2.acct_app.iterator(); localIterator.hasNext();) {Integer localObject3 = (Integer)localIterator.next();
      if ((((Integer)localObject3).intValue() == -1) || (paramCapability1.acct_app.contains(localObject3)) || (paramCapability1.acct_app.contains(Integer.valueOf(-1))))
      {

        localCapability.addAcctApp(((Integer)localObject3).intValue()); }
    }
    for (Iterator localIterator = paramCapability2.auth_vendor.iterator(); localIterator.hasNext();) {VendorApplication localObject4 = (VendorApplication)localIterator.next();
      
      if (paramCapability1.isAllowedAuthApp(((VendorApplication)localObject4).vendor_id, ((VendorApplication)localObject4).application_id))
        localCapability.addVendorAuthApp(((VendorApplication)localObject4).vendor_id, ((VendorApplication)localObject4).application_id);
    }
    for (Iterator localIterator = paramCapability2.acct_vendor.iterator(); localIterator.hasNext();) {VendorApplication localObject5 = (VendorApplication)localIterator.next();
      
      if (paramCapability1.isAllowedAcctApp(((VendorApplication)localObject5).vendor_id, ((VendorApplication)localObject5).application_id))
        localCapability.addVendorAcctApp(((VendorApplication)localObject5).vendor_id, ((VendorApplication)localObject5).application_id);
    }
    return localCapability;
  }
}
