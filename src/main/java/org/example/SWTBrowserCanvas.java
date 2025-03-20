package org.example;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.DPIUtil;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Implementation of an AWT {@link Canvas} that embeds an SWT {@link Browser} component.
 * <p>
 * With contemporary versions of SWT, the Webkit browser is the default implementation.
 * <p>
 * To embed an SWT component inside of a Swing component there are a number of important
 * considerations (all of which comprise this implementation):
 * <ul>
 *   <li>A background thread must be created to process the SWT event dispatch loop.</li>
 *   <li>The browser component can not be created until after the hosting Swing component (e.g. the
 *       JFrame) has been made visible - usually right after <code>frame.setVisible(true).</code></li>
 *   <li>To cleanly dispose the native browser component, it is necessary to perform that clean
 *       shutdown from inside a {@link WindowListener#windowClosing(WindowEvent)} implementation in
 *       a listener registered on the hosting JFrame.</li>
 *   <li>On Linux, the <code>sun.awt.xembedserver</code> system property must be set.</li>
 * </ul>
 */
public final class SWTBrowserCanvas extends Canvas {

    /**
     * Required for Linux, harmless for other OS.
     * <p>
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=161911">SWT Component Not Displayed Bug</a>
     */
    static {
        System.setProperty("sun.awt.xembedserver", "true");
    }

    /**
     * SWT browser component reference.
     */
    private final AtomicReference<Browser> browserReference = new AtomicReference<>();

    /**
     * SWT event dispatch thread reference.
     */
    private final AtomicReference<SwtThread> swtThreadReference = new AtomicReference<>();

    /**
     * Get the native browser instance.
     *
     * @return browser, may be <code>null</code>
     */
    public Browser getBrowser() {
        return browserReference.get();
    }

    static double scaleX;
    static double scaleY;

    static {
        GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        AffineTransform displayTransform = graphicsConfiguration.getDefaultTransform();
        scaleX = displayTransform.getScaleX(); // Windows OS Display X scaling.
        scaleY = displayTransform.getScaleY(); // Windows OS Display Y scaling.
    }

    @Override
    public Dimension getSize() {
        Dimension size = super.getSize();
        size.width = (int) (size.width * SWTBrowserCanvas.scaleX);
        size.height = (int) (size.height * SWTBrowserCanvas.scaleY);
        return size;
    }

    /**
     * Navigate to a URL.
     *
     * @param url URL
     */
    public void setUrl(final String url) {
        // This action must be executed on the SWT thread
        getBrowser().getDisplay().asyncExec(() -> {
            getBrowser().setUrl(url);
        });
    }

    /**
     * Create the browser canvas component.
     * <p>
     * This must be called <strong>after</strong> the parent application Frame is made visible -
     * usually directly after <code>frame.setVisible(true)</code>.
     * <p>
     * This method creates the background thread, which in turn creates the SWT components and
     * handles the SWT event dispatch loop.
     * <p>
     * This method will block (for a very short time) until that thread has successfully created
     * the native browser component (or an error occurs).
     *
     * @return <code>true</code> if the browser component was successfully created; <code>false if it was not</code/
     */
    public boolean initialise() {
        CountDownLatch browserCreatedLatch = new CountDownLatch(1);
        SwtThread swtThread = new SwtThread(browserCreatedLatch);
        swtThreadReference.set(swtThread);
        swtThread.start();
        boolean result;
        try {
            browserCreatedLatch.await();
            result = browserReference.get() != null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    /**
     * Dispose the browser canvas component.
     * <p>
     * This should be called from a {@link WindowListener#windowClosing(WindowEvent)} implementation.
     */
    public void dispose() {
        browserReference.set(null);
        SwtThread swtThread = swtThreadReference.getAndSet(null);
        if (swtThread != null) {
            swtThread.interrupt();
        }
    }

    /**
     * Implementation of a thread that creates the browser component and then implements an event
     * dispatch loop for SWT.
     */
    private class SwtThread extends Thread {

        /**
         * Initialisation latch.
         */
        private final CountDownLatch browserCreatedLatch;

        /**
         * Create a thread.
         *
         * @param browserCreatedLatch initialisation latch.
         */
        private SwtThread(CountDownLatch browserCreatedLatch) {
            this.browserCreatedLatch = browserCreatedLatch;
        }

        @Override
        public void run() {
            // First prepare the SWT components...
            Display display;
            Shell shell;
            try {
                display = new Display();
                shell = SWT_AWT.new_Shell(display, SWTBrowserCanvas.this);
                shell.setLayout(new FillLayout());
                browserReference.set(new Browser(shell, SWT.EMBEDDED | SWT.EDGE));
                display.asyncExec(() -> {
                    Dimension dim = SWTBrowserCanvas.this.getSize();
                    shell.setSize(DPIUtil.autoScaleDown(new Point(dim.width, dim.height))); // To Points
                });
            } catch (Exception e) {
                e.printStackTrace();
                return;
            } finally {
                // Guarantee the count-down so as not to block the caller, even in case of error -
                // there is a theoretical (rare) chance of failure to initialise the SWT components
                browserCreatedLatch.countDown();
            }

            // Execute the SWT event dispatch loop...
            try {
                shell.open();
                while (!isInterrupted() && !shell.isDisposed()) {
                    if (!display.readAndDispatch()) {
                        display.sleep();
                    }
                }
                browserReference.set(null);
                shell.dispose();
                display.dispose();
            } catch (Exception e) {
                e.printStackTrace();
                interrupt();
            }
        }
    }

    /**
     * Example implementation.
     *
     * @param args command-line arguments (unused)
     * @return
     */
    public static void main(String[] args) {
        final SWTBrowserCanvas browserCanvas = new SWTBrowserCanvas();
        browserCanvas.setSize(1200, 800);

        AtomicReference<JFrame> owner = new AtomicReference<>(null);

        createFrame("First Frame", browserCanvas, owner);
        createFrame("Second Frame", browserCanvas, owner);

        // Initialise the native browser component, and if successful...
        if (browserCanvas.initialise()) {
            // ...navigate to the desired URL
            browserCanvas.setUrl("https://www.google.co.uk");
        } else {
            System.out.println("Failed to initialise browser");
        }
    }

    private static void createFrame(String frameName, SWTBrowserCanvas browserCanvas, AtomicReference<JFrame> owner) {
        JFrame frame = new JFrame(frameName);

        JPanel contentPane = new JPanel(new BorderLayout());
        JButton button = new JButton("Take Ownership");
        button.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!owner.compareAndSet(frame, frame)) {
                    contentPane.add(browserCanvas, BorderLayout.CENTER);
                }
            }
        });
        contentPane.add(button, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(contentPane);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (owner.get() == frame) {
                    browserCanvas.dispose();
                }
            }
        });

        frame.pack();
        frame.setVisible(true);
    }

}
