/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.resources.servicecore;

import com.sun.jini.config.Config;
import com.sun.jini.landlord.*;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.lease.Lease;
import net.jini.core.lease.LeaseDeniedException;

import net.jini.core.lease.UnknownLeaseException;
import net.jini.export.Exporter;
import net.jini.id.ReferentUuid;
import net.jini.id.Uuid;
import net.jini.id.UuidFactory;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.security.TrustVerifier;
import net.jini.security.proxytrust.ServerProxyTrust;
import org.rioproject.config.ExporterConfig;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The LandlordLessor manages leased resources using the Landlord protocol.
 * 
 * <p>
 * The LandlordLessor supports the following configuration entries; where each
 * configuration entry name is associated with the component name <span *=""
 * style="font-family: monospace;">org.rioproject.resources.servicecore</span>
 * <br>
 * </p>
 * <ul>
 * <li><span style="font-weight: bold;">landlordExporter </span> <table
 * cellpadding="2" *="" cellspacing="2" border="0" style="text-align: left;
 * width: 100%;"> <tbody>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Type:</td>
 * <td style="vertical-align: top;">{@link net.jini.export.Exporter}</td>
 * </tr>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Default:</td>
 * <td style="vertical-align: top;">A new {@link net.jini.jeri.BasicJeriExporter} with
 * <ul>
 * <li>a {@link net.jini.jeri.tcp.TcpServerEndpoint} created on a random port,
 * </li>
 * <li>a {@link net.jini.jeri.BasicILFactory}, </li>
 * <li>distributed garbage collection turned off, </li>
 * <li>keep alive on. </li>
 * </ul>
 * </td>
 * </tr>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Description:</td>
 * <td style="vertical-align: top;">Specifies the Exporter to use to export
 * this service. This entry is obtained at service start and restart.</td>
 * </tr>
 * </tbody> </table></li>
 * </ul>
 *
 * <ul>
 * <li><span style="font-weight: bold;">landlordLeasePeriodPolicy </span> <table
 * cellpadding="2" *="" cellspacing="2" border="0" style="text-align: left;
 * width: 100%;"> <tbody>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Type:</td>
 * <td style="vertical-align: top;">{@link com.sun.jini.landlord.LeasePeriodPolicy}</td>
 * </tr>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Default:</td>
 * <td style="vertical-align: top;">A new
 * {@link com.sun.jini.landlord.FixedLeasePeriodPolicy} that allows
 * leases up to one day, and grants one hour leases for duration requests of
 * {@link net.jini.core.lease.Lease#ANY}</code>
 * <br>
 * </td>
 * </tr>
 * <tr>
 * <td style="vertical-align: top; text-align: right; font-weight: bold;">Description:</td>
 * <td style="vertical-align: top;">Policy used to determine the length of
 * initial grants and renewals of the leases on entries. Obtained at service
 * start and restart.</td>
 * </tr>
 * </tbody> </table></li>
 * </ul>
 *
 * @author Dennis Reedy
 */
public class LandlordLessor extends ResourceLessor implements Landlord,
                                                              ReferentUuid,
                                                              ServerProxyTrust {
    /** The default time for a Lease: 1 hour */
    public static final long DEFAULT_LEASE_TIME = 1000 * 60 * 60;
    /** The maximum time for a Lease: 1 day */
    public static final long DEFAULT_MAX_LEASE_TIME = 1000 * 60 * 60  * 24;
    /** This LandlordLessor's uuid */
    Uuid uuid;
    /** The Remote Landlord */
    Landlord landlord;
    /** The Exporter for the Landlord */
    Exporter exporter;
    /** Factory we use to create leases */
    LeaseFactory leaseFactory;
    /** LeasePolicy */
    LeasePeriodPolicy leasePolicy;
    /** Component for reading configuration entries and getting the Logger */
    static final String COMPONENT = "org.rioproject.resources.servicecore";
    static Logger logger = Logger.getLogger(COMPONENT);

    /**
     * Create a LandlordLessor
     * 
     * @param config The Configuration object used to initialize operational
     * values.
     *
     * @throws RemoteException if errors occur setting up infrastructure
     */
    public LandlordLessor(Configuration config) throws RemoteException {
        this(config, null);
    }
    /**
     * Create a LandlordLessor
     * 
     * @param config The Configuration object used to initialize operational
     * values.
     * @param leasePolicy A LeasePeriodPolicy object to be used for the 
     * LandlordLessor.
     *
     * @throws RemoteException if errors occur setting up infrastructure
     */
    public LandlordLessor(Configuration config, LeasePeriodPolicy leasePolicy)
    throws RemoteException {
        super();
        if (config == null)
            throw new NullPointerException("config is null");
           
        /* Get the LeasePeriodPolicy */
        final LeasePeriodPolicy defaultLeasePeriodPolicy =
            (leasePolicy==null?
                new FixedLeasePeriodPolicy(DEFAULT_MAX_LEASE_TIME,
                                           DEFAULT_LEASE_TIME):
                leasePolicy);

        try {
            this.leasePolicy =
            (LeasePeriodPolicy)Config.getNonNullEntry(config,
                                                      COMPONENT,
                                                      "landlordLeasePeriodPolicy",
                                                      LeasePeriodPolicy.class,
                                                      defaultLeasePeriodPolicy);
        } catch (ConfigurationException e) {
            logger.log(Level.WARNING,
                       "Getting LeasePeriodPolicy in LandlordLessor",
                       e);
        }
        
        /* Create the default Exporter */
        final Exporter defaultExporter = 
            new BasicJeriExporter(TcpServerEndpoint.getInstance(0), 
                                  new BasicILFactory());
        try {            
            exporter = ExporterConfig.getExporter(config,
                                                COMPONENT,
                                                "landlordExporter", 
                                                defaultExporter);
        } catch (ConfigurationException e) {
            logger.log(Level.WARNING, "Getting Exporter in LandlordLessor", e);
        }
        if(exporter==null)
            exporter = defaultExporter;
        
        landlord = (Landlord) exporter.export(this);
        uuid = UuidFactory.generate(); 
        leaseFactory = new LeaseFactory(landlord, uuid);
    }

    /**
     * Stop the LandlordLessor
     * 
     * @param force if true, unexports the LandlordLessor even if there are
     * pending or in-progress calls; if false, only unexports the LandlordLessor
     * if there are no pending or in-progress calls
     *
     * @return True or false if the unexport was succesful
     */
    public boolean stop(boolean force) {
        super.stop();
        boolean unexported = false;
        try {
            unexported = exporter.unexport(force);
        } catch (Exception e) {
            if(logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST,
                           "Unexporting LandlordLessor",
                           e);
            }
        }
        return (unexported);
    }

    /**
     * Concrete implementation of parent class
     * 
     * @see org.rioproject.resources.servicecore.ResourceLessor#newLease
     */
    public Lease newLease(LeasedResource resource, long duration)
    throws LeaseDeniedException {
        LeasePeriodPolicy.Result leasePeriod = leasePolicy.grant(resource,
                                                                 duration);
        Lease lease = leaseFactory.newLease(resource.getCookie(),
                                            leasePeriod.expiration);
        resource.setExpiration(leasePeriod.expiration);
        addLeasedResource(resource);
        notifyLeaseRegistration(resource);
        return (lease);
    }

    /**
     * Called by the lease when its <code>renew</code> method is called. <br>
     * 
     * @param cookie Associated with the lease when it was created
     * @param extension The duration argument passed to the
     * <code>Lease.renew()</code> call
     * @return The new duration the lease should have
     */
    public long renew(Uuid cookie, long extension) throws LeaseDeniedException,
    UnknownLeaseException {
        LeasedResource resource = getLeasedResource(cookie);
        long granted;
        if (resource == null)
            throw new UnknownLeaseException("No lease for cookie: " + cookie);        
        synchronized (resource) {
            long now = System.currentTimeMillis();
            if (resource.getExpiration() <= now) {                
                UnknownLeaseException e = 
                    new UnknownLeaseException("Lease has already expired");
                if(logger.isLoggable(Level.FINEST)) {
                    logger.finest("Lease has already expired by ["+
                                  (now-resource.getExpiration())+"] millis, "+
                                  "["+(now-resource.getExpiration())/1000+"] "+
                                  "seconds");                
                    logger.throwing(this.getClass().getName(), "renew", e);
                }
                throw e;
            }
            LeasePeriodPolicy.Result leasePeriod = leasePolicy.renew(resource,
                                                                     extension);
            resource.setExpiration(leasePeriod.expiration);
            granted = leasePeriod.duration;
            addLeasedResource(resource);
            notifyLeaseRenewal(resource);
        }
        return (granted);        
    }

    /**
     * Called by the lease map when its <code>renewAll</code> method is
     * called.
     * 
     * @param cookie Associated with each lease when it was created <br>
     * @param extension The duration argument for each lease from the map
     * @return The results of the renew
     */
    public Landlord.RenewResults renewAll(Uuid[] cookie, long[] extension) {
        int size = cookie.length;
        long[] granted = new long[size];
        Exception[] denied = null;
        for (int i = 0; i < size; i++) {
            try {
                granted[i] = renew(cookie[i], extension[i]);
                denied[i] = null;
            } catch (Exception e) {
                if (denied == null)
                    denied = new Exception[size];
                denied[i] = e;
            }
        }
        return (new Landlord.RenewResults(granted, denied));
    }

    /**
     * Called by the lease when its <code>cancel</code> method is called. <br>
     * 
     * @param cookie Associated with the lease when it was created
     */
    public void cancel(Uuid cookie) throws UnknownLeaseException {
        if (!remove(cookie))
            throw new UnknownLeaseException("No lease for cookie: " + cookie);
    }

    /**
     * Called by the lease map when its <code>cancelAll</code> method is
     * called.
     * 
     * @param cookies Associated with the lease when it was created <br>
     */
    public Map cancelAll(Uuid[] cookies) {
        int size = cookies.length;
        Map<Uuid, Exception> exceptionMap = null;
        for (int i = 0; i < size; i++) {
            try {
                cancel(cookies[i]);
            } catch (Exception e) {
                if (exceptionMap == null) {
                    exceptionMap = new HashMap<Uuid, Exception>();
                }
                exceptionMap.put(cookies[i], e);
            }
        }
        /*
         * If all the leases specified in the cookies could be cancelled return
         * null. Otherwise, return a Map that for each failed cancel attempt
         * maps the corresponding cookie object to an exception describing the
         * failure.
         */
        return (exceptionMap);
    }

    public TrustVerifier getProxyVerifier() throws RemoteException {
        return(new LandlordProxyVerifier(landlord, uuid));
    }

    /**
     * Return the <code>Uuid</code> that has been assigned to the resource this
     * proxy represents.
     *
     * @return the <code>Uuid</code> associated with the resource this proxy
     *         represents. Will not return <code>null</code>.
     */
    public Uuid getReferentUuid() {
        return(uuid);
    }
}
