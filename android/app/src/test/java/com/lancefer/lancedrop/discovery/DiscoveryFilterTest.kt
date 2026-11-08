package com.lancefer.lancedrop.discovery

import com.lancefer.lancedrop.model.DiscoveredPeer
import com.lancefer.lancedrop.model.OsType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoveryFilterTest {

    private lateinit var filter: DiscoveryFilter
    private val localDeviceId = "android-Pixel8-9876"
    private val localDeviceName = "Mon Téléphone Pixel"
    private val localWifiIp = "192.168.1.45"
    private val localVpnIp = "10.8.0.3"

    @Before
    fun setUp() {
        filter = DiscoveryFilter(
            localDeviceId = localDeviceId,
            localDeviceNames = setOf(localDeviceName, "Pixel 8"),
            localAddresses = setOf(localWifiIp, localVpnIp)
        )
    }

    @Test
    fun testRejectsLoopbackAndLocalAddresses() {
        assertTrue("127.0.0.1 must be identified as self", filter.isSelf("remote-1", "127.0.0.1"))
        assertTrue("::1 must be identified as self", filter.isSelf("remote-1", "::1"))
        assertTrue("0.0.0.0 must be identified as self", filter.isSelf("remote-1", "0.0.0.0"))
        assertTrue("localhost must be identified as self", filter.isSelf("remote-1", "localhost"))
        assertTrue("127.0.1.1 loopback subnet must be identified as self", filter.isSelf("remote-1", "127.0.1.1"))
        assertTrue("Active Wi-Fi IP must be identified as self", filter.isSelf("remote-1", localWifiIp))
        assertTrue("Active VPN IP must be identified as self", filter.isSelf("remote-1", localVpnIp))
        assertTrue("IP with IPv6 scope ID must be identified as self", filter.isSelf("remote-1", "$localWifiIp%wlan0"))
    }

    @Test
    fun testRejectsLocalDeviceId() {
        assertTrue(
            "Exact local device ID match must be rejected as self even with unknown IP",
            filter.isSelf(senderDeviceId = localDeviceId, remoteAddress = "192.168.1.99")
        )
    }

    @Test
    fun testRejectsSelfDeviceNameAndPrefix() {
        assertTrue(
            "Exact local device name must be rejected",
            filter.isSelf("remote-id", "192.168.1.99", localDeviceName)
        )
        assertTrue(
            "Case-insensitive local device name match must be rejected",
            filter.isSelf("remote-id", "192.168.1.99", "mon téléphone pixel")
        )
        assertTrue(
            "Service name starting with LanceDrop-Android prefix must be rejected",
            filter.isSelf("remote-id", "192.168.1.99", "LanceDrop-Android-Pixel8")
        )
    }

    @Test
    fun testAcceptsRemoteExternalPeers() {
        val remotePeer = DiscoveredPeer(
            id = "pc-windows-office-1234",
            name = "PC-Bureau-Windows",
            ipAddress = "192.168.1.150",
            port = 42420,
            osType = OsType.WINDOWS
        )

        assertFalse(
            "Legitimate remote peer IP must not be flagged as self",
            filter.isSelf(remotePeer.id, remotePeer.ipAddress, remotePeer.name)
        )
        assertTrue(
            "Legitimate remote peer should be accepted",
            filter.shouldAcceptPeer(remotePeer)
        )
    }

    @Test
    fun testFilterPeersListPurgesSelf() {
        val remotePeer1 = DiscoveredPeer(
            id = "desktop-pc-01",
            name = "PC-Desktop",
            ipAddress = "192.168.1.100",
            port = 42420,
            osType = OsType.WINDOWS
        )
        val remotePeer2 = DiscoveredPeer(
            id = "macbook-pro-02",
            name = "MacBook-Salon",
            ipAddress = "192.168.1.105",
            port = 42420,
            osType = OsType.MACOS
        )
        val selfPeerById = DiscoveredPeer(
            id = localDeviceId,
            name = "Autre Nom",
            ipAddress = "192.168.1.200",
            port = 42420
        )
        val selfPeerByIp = DiscoveredPeer(
            id = "fake-remote-id",
            name = "Inconnu",
            ipAddress = localWifiIp,
            port = 42420
        )
        val selfPeerByName = DiscoveredPeer(
            id = "fake-remote-id-2",
            name = "LanceDrop-Android-Device",
            ipAddress = "192.168.1.205",
            port = 42420
        )

        val mixedList = listOf(remotePeer1, selfPeerById, remotePeer2, selfPeerByIp, selfPeerByName)
        val filteredList = filter.filterPeers(mixedList)

        assertEquals("Filtered list must contain exactly 2 remote peers", 2, filteredList.size)
        assertEquals("First remaining peer should be PC-Desktop", remotePeer1.id, filteredList[0].id)
        assertEquals("Second remaining peer should be MacBook-Salon", remotePeer2.id, filteredList[1].id)
    }
}
