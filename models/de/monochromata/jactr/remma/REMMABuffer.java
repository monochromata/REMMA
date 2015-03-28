package de.monochromata.jactr.remma;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.util.Collection;

import javafx.collections.SetChangeListener;

import org.jactr.core.buffer.delegate.DefaultDelegatedRequestableBuffer6;
import org.jactr.core.buffer.delegate.IRequestDelegate;
import org.jactr.core.chunk.IChunk;

import de.monochromata.jactr.twm.Referentialisation;
import de.monochromata.jactr.twm.WordActivation;

public class REMMABuffer extends DefaultDelegatedRequestableBuffer6 {
	
	public REMMABuffer(REMMAModule module) {
		super(IREMMA.BUFFER_NAME, module);
	}
	
	@Override
	protected boolean shouldCopyOnInsertion(IChunk chunk) {
		return false;
	}

	/**
	 * The buffer (actually {@link REMMARequestDelegate}
	 * handles encoding, removal of source chunks should
	 * not trigger their encoding. 
	 */
	@Override
	public boolean handlesEncoding() {
		return true;
	}

	@Override
	protected void grabReferences() {
		super.grabReferences();
		addRequestDelegate(
			new REMMARequestDelegate((REMMAModule)getModule(), this));
	}

}
