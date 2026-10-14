package com.example.client;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** Draws a small head-sized marker for each other player, with depth testing disabled. */
public final class HeadEspRenderer {
	private static final RenderPipeline HEAD_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("playerheadesp", "pipeline/player_heads"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build());
	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static HeadEspRenderer instance;

	private BufferBuilder buffer;
	private MappableRingBuffer vertexBuffer;

	private HeadEspRenderer() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::extractAndDrawHeads);
	}

	public static void initialize() {
		if (instance == null) instance = new HeadEspRenderer();
	}

	private void extractAndDrawHeads(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (!ExampleModClient.ESP_ENABLED || client.level == null || client.player == null) return;

		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		buffer = new BufferBuilder(ALLOCATOR, HEAD_PIPELINE.getVertexFormatMode(), HEAD_PIPELINE.getVertexFormat());

		for (Player player : client.level.players()) {
			if (player == client.player || player.isRemoved() || player.isSpectator()) continue;
			Vec3 pos = player.position();
			if (pos.distanceToSqr(camera) > 128.0 * 128.0) continue;

			// A compact cube centered on the player's head; the body receives no ESP geometry.
			float half = 0.22f;
			float centerY = (float) (pos.y + player.getEyeHeight() - 0.08);
			renderBox(matrices.last().pose(), buffer,
					(float) pos.x - half, centerY - half, (float) pos.z - half,
					(float) pos.x + half, centerY + half, (float) pos.z + half,
					0.15f, 0.9f, 1.0f, 1.0f);
		}
		matrices.popPose();

		MeshData mesh = buffer.build();
		buffer = null;
		if (mesh == null) return;
		MeshData.DrawState drawState = mesh.drawState();
		VertexFormat format = drawState.format();
		GpuBuffer vertices = upload(drawState, format, mesh);
		draw(client, HEAD_PIPELINE, mesh, drawState, vertices, format);
		vertexBuffer.rotate();
	}

	private static void renderBox(Matrix4fc matrix, BufferBuilder out, float x0, float y0, float z0,
			float x1, float y1, float z1, float r, float g, float b, float a) {
		face(out, matrix, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b,a);
		face(out, matrix, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, r,g,b,a);
		face(out, matrix, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b,a);
		face(out, matrix, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, r,g,b,a);
		face(out, matrix, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, r,g,b,a);
		face(out, matrix, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, r,g,b,a);
	}

	private static void face(BufferBuilder out, Matrix4fc m,
			float ax,float ay,float az, float bx,float by,float bz,
			float cx,float cy,float cz, float dx,float dy,float dz,
			float r,float g,float b,float a) {
		out.addVertex(m, ax,ay,az).setColor(r,g,b,a);
		out.addVertex(m, bx,by,bz).setColor(r,g,b,a);
		out.addVertex(m, cx,cy,cz).setColor(r,g,b,a);
		out.addVertex(m, dx,dy,dz).setColor(r,g,b,a);
	}

	private GpuBuffer upload(MeshData.DrawState state, VertexFormat format, MeshData mesh) {
		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> "Player Head ESP", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mapped = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
		}
		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData mesh,
			MeshData.DrawState state, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;
		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
			indexType = state.indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shape = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shape.getBuffer(state.indexCount());
			indexType = shape.type();
		}
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
				RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Player Head ESP markers", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0 / format.getVertexSize(), 0, state.indexCount(), 1);
		}
		mesh.close();
	}

	public static void close() {
		ALLOCATOR.close();
		if (instance != null && instance.vertexBuffer != null) {
			instance.vertexBuffer.close();
			instance.vertexBuffer = null;
		}
	}
}
